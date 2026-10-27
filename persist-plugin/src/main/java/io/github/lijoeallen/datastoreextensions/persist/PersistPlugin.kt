@file:OptIn(ExperimentalCompilerApi::class, UnsafeDuringIrConstructionAPI::class)

package io.github.lijoeallen.datastoreextensions.persist

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.builders.IrSingleStatementBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

const val PLUGIN_ID = "io.github.lijoeallen.datastoreextensions.persist"

private const val ANNOTATION_FQ_NAME = "io.github.lijoeallen.datastoreextensions.Persisted"
private const val BACKEND_PACKAGE = "io.github.lijoeallen.datastoreextensions"
private const val STORE_CLASS_FQ_NAME = "io.github.lijoeallen.datastoreextensions.PersistedStore"

// key 段之间的分隔符（存储契约的一部分，与库文档一致）
private const val KEY_SEPARATOR = "-"

// 注解参数名（与 Persisted.kt 的 annotation class Persisted(key) 一一对应）
private const val PARAM_KEY = "key"

// 库的后端函数命名（与库内函数一一对应，勿改名）
private const val GETTER_FN_PREFIX = "persistedGet"
private const val SETTER_FN_PREFIX = "persistedSet"
private const val PERSISTED_FN_NAME = "persisted"
private const val RESET_KEY_FN_NAME = "resetKey"
private const val RESET_SCOPE_KEY_FN_NAME = "resetScopeKey"

/**
 * 按 CallableId 查找库函数符号（位于依赖模块 datastore-extensions 中）。
 *
 * Kotlin 2.4 弃用了 referenceFunctions，建议的替代 finderForBuiltins()/finderForSource()
 * 分别只覆盖内置声明与本模块源码，均不含跨模块（依赖库）符号，暂无官方替代 API，
 * 先抑制弃用警告，待官方提供跨模块 finder 后迁移。
 */
@Suppress("DEPRECATION")
private fun IrPluginContext.findBackendFunction(name: String): IrSimpleFunctionSymbol? =
    referenceFunctions(CallableId(FqName(BACKEND_PACKAGE), Name.identifier(name))).firstOrNull()

class PersistCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}

class PersistPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(PersistIrGenerationExtension())
    }
}

/**
 * IR 阶段两件事：
 *
 * 1. 重写 @Persisted 属性的访问器：
 *   getter → return persistedGet<Type>("<key>", <默认值>)
 *   setter → persistedSet<Type>("<key>", value)
 *
 * 2. 重写 PersistedStore.reset(属性引用)/resetScope(类引用) 调用点：
 *   reset(TimerState::switch) → resetKey("demo-Audio-switch")
 *   resetScope(TimerState::class) → resetScopeKey("demo-Audio")
 *   key 在编译期算好并内联，零反射、R8 安全；引用目标非 @Persisted 时报编译错
 *
 * key = 各层 key 段以 "-" 连接（多级作用域逐层追加）：
 *   - 属性段 = 属性 @Persisted 的 key（缺省为属性名）；以 "/" 开头为绝对 key（忽略作用域段）
 *   - 对象段 = 对象 @Persisted 的 key（缺省为对象简单名），嵌套时拼接外层作用域
 *
 * @Persisted object 是持久化作用域：作用域内所有 var 与嵌套对象自动纳入；
 * 作用域外（顶层/普通类）仅带 @Persisted 的成员持久化（无作用域前缀）。
 *
 * 两种声明写法均重写：`var abc = 100`（默认值取 backing field）、
 * `var xyz by persisted(1)`（默认值取 persisted(字面量) 参数）；
 * 非 persisted() 的自定义委托保持运行时委托语义，不动。
 */
class PersistIrGenerationExtension : IrGenerationExtension {

    // 编译期 key 查重：key → 首个声明的归属描述。增量编译/跨模块场景由库的
    // PersistedKeyConflictException（同 key 不同默认值）运行期兜底
    private val seenKeys = mutableMapOf<String, String>()

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.files.forEach { file ->
            file.declarations.forEach { declaration ->
                when (declaration) {
                    is IrProperty ->
                        if (hasPersisted(declaration)) {
                            transform(declaration, pluginContext, scope = null, owner = "<顶层>")
                        }
                    is IrClass -> processClass(declaration, pluginContext, parentScope = null, parentOwner = "")
                    else -> Unit
                }
            }
            // 声明处理完再重写函数体内的 reset 调用点
            file.transformChildren(ResetCallRewriter(pluginContext), null)
        }
    }

    /** 递归处理类及其嵌套类：scope != null 表示处于持久化作用域内（全部成员自动纳入） */
    private fun processClass(
        irClass: IrClass,
        context: IrPluginContext,
        parentScope: String?,
        parentOwner: String,
    ) {
        val scope = scopeSegment(irClass, parentScope)
        val owner = if (parentOwner.isEmpty()) irClass.name.asString() else "$parentOwner.${irClass.name.asString()}"
        irClass.declarations.forEach { declaration ->
            when (declaration) {
                is IrProperty -> when {
                    scope != null -> transform(declaration, context, scope, owner)
                    hasPersisted(declaration) -> transform(declaration, context, null, owner)
                }
                is IrClass -> processClass(declaration, context, scope, owner)
                else -> Unit
            }
        }
    }

    private fun hasPersisted(property: IrProperty): Boolean =
        property.annotations.any { it.isPersistedAnnotation() }

    /**
     * 类的 key 段：作用域内（parentScope != null）自动纳入，段 = 注解 key（缺省简单名）叠加外层；
     * 作用域外仅 @Persisted 类开新作用域，段 = key（缺省简单名）；非 @Persisted 类返回 null
     */
    private fun scopeSegment(irClass: IrClass, parentScope: String?): String? {
        val annotation = irClass.annotations.firstOrNull { it.isPersistedAnnotation() }
        if (parentScope != null) {
            val own = annotation?.paramValue(PARAM_KEY)?.takeIf { it.isNotEmpty() }
                ?: irClass.name.asString()
            return "$parentScope$KEY_SEPARATOR$own"
        }
        if (annotation != null) {
            return annotation.paramValue(PARAM_KEY)?.takeIf { it.isNotEmpty() }
                ?: irClass.name.asString()
        }
        return null
    }

    private fun IrConstructorCall.isPersistedAnnotation(): Boolean =
        type.classFqName?.asString() == ANNOTATION_FQ_NAME

    /** 注解参数值（按参数名取）；参数不存在或空串视为未指定 */
    private fun IrConstructorCall.paramValue(paramName: String): String? {
        // 注解构造器无 receiver 参数，parameters 即全部值参数，索引与 arguments 对齐
        val index = symbol.owner.parameters.indexOfFirst { it.name.asString() == paramName }
        if (index < 0) return null
        return (arguments.getOrNull(index) as? IrConst)?.value as? String
    }

    private fun transform(
        property: IrProperty,
        context: IrPluginContext,
        scope: String?,
        owner: String,
    ) {
        val getter = property.getter ?: return
        val name = property.name.asString()
        if (name.startsWith("$")) return // 跳过编译器合成属性（如 Compose 的 $stable）
        val start = property.startOffset
        val end = property.endOffset

        val annotation = property.annotations.firstOrNull { it.isPersistedAnnotation() }
        val key = computeKey(property, annotation, scope, name, context) ?: return

        // 编译期查重：同模块内两个声明算出同一 key 直接报错
        val prev = seenKeys.putIfAbsent(key, "$owner.$name")
        if (prev != null) {
            context.reportError(
                "[@Persisted] 存储 key 重复：'$key' 同时被 $prev 与 $owner.$name 声明，" +
                    "两属性将共享同一存储；请调整 key 或作用域"
            )
            return
        }

        val typeSuffix = typeSuffix(getter.returnType, context, name) ?: return
        val getFun = context.findBackendFunction(GETTER_FN_PREFIX + typeSuffix)
        if (getFun == null) {
            context.reportError("[@Persisted] 找不到后端函数 $GETTER_FN_PREFIX$typeSuffix（属性 '$name'）")
            return
        }

        val getBuilder = IrSingleStatementBuilder(context, Scope(getter.symbol), start, end, IrStatementOrigin.INVOKE)

        // 默认值：委托写法取 persisted(<字面量>) 参数；纯注解写法读 backing field
        val defaultExpr: IrExpression = if (property.isDelegated) {
            defaultFromDelegate(property, context, name, start, end) ?: return
        } else {
            val field = property.backingField ?: return
            getBuilder.irGetField(
                getter.dispatchReceiverParameter?.let { getBuilder.irGet(it) },
                field,
                field.type,
            )
        }

        // getter: return persistedGet<Type>("<key>", <默认值>)
        val getCall = getBuilder.irCall(getFun, getter.returnType, 2, IrStatementOrigin.INVOKE)
        getCall.arguments[0] = getBuilder.irString(key)
        getCall.arguments[1] = defaultExpr
        getter.body = getBuilder.irBlockBody(start, end) {
            +IrReturnImpl(start, end, context.irBuiltIns.nothingType, getter.symbol, getCall)
        }

        // setter: persistedSet<Type>("<key>", value)
        val setter = property.setter ?: return
        val setFun = context.findBackendFunction(SETTER_FN_PREFIX + typeSuffix)
        if (setFun == null) {
            context.reportError("[@Persisted] 找不到后端函数 $SETTER_FN_PREFIX$typeSuffix（属性 '$name'）")
            return
        }
        val setBuilder = IrSingleStatementBuilder(context, Scope(setter.symbol), start, end, IrStatementOrigin.INVOKE)
        val setCall = setBuilder.irCall(setFun, setter.returnType, 2, IrStatementOrigin.INVOKE)
        setCall.arguments[0] = setBuilder.irString(key)
        setCall.arguments[1] = setBuilder.irGet(setter.parameters.last())
        setter.body = setBuilder.irBlockBody(start, end) { +setCall }
    }

    /**
     * 属性的最终存储 key。`/` 开头为绝对 key：忽略全部作用域段（存量 flat key 收拢零迁移）；
     * 绝对 key 为空报编译错并返回 null
     */
    private fun computeKey(
        property: IrProperty,
        annotation: IrConstructorCall?,
        scope: String?,
        name: String,
        context: IrPluginContext,
    ): String? {
        val raw = annotation?.paramValue(PARAM_KEY)?.takeIf { it.isNotEmpty() }
        return when {
            raw != null && raw.startsWith("/") -> {
                val absolute = raw.substring(1)
                if (absolute.isEmpty()) {
                    context.reportError("[@Persisted] 绝对 key（\"/\" 开头）不能为空（属性 '$name'）")
                    null
                } else {
                    absolute
                }
            }
            else -> {
                val segment = raw ?: name
                scope?.let { "$it$KEY_SEPARATOR$segment" } ?: segment
            }
        }
    }

    /**
     * 委托写法 `by persisted(<字面量>)`：提取字面量默认值（复制一份，原表达式仍留在委托字段里）。
     * 非 persisted() 委托返回 null（保持运行时委托语义）；默认值非字面量报编译错误。
     */
    private fun defaultFromDelegate(
        property: IrProperty,
        context: IrPluginContext,
        name: String,
        start: Int,
        end: Int,
    ): IrExpression? {
        val initCall = property.backingField?.initializer?.expression as? IrCall
        val persistedSymbol = context.findBackendFunction(PERSISTED_FN_NAME)
        if (initCall == null || persistedSymbol == null || initCall.symbol != persistedSymbol) return null
        val arg = initCall.arguments.filterNotNull().firstOrNull() as? IrConst
        if (arg == null) {
            context.reportError(
                "[@Persisted] 委托写法的默认值仅支持字面量（属性 '$name'），或改用纯注解写法 `var $name = <默认值>`"
            )
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return IrConstImpl(
            start, end,
            arg.type,
            arg.kind,
            arg.value,
        )
    }

    /**
     * messageCollector 在 Kotlin 2.4 被弃用，建议迁移 diagnosticReporter；但其
     * KtSourcelessDiagnosticFactory 需要填充渲染 Map（put 为 internal，第三方无法调用），
     * 空 Map 在报错时会抛 IllegalStateException（实测）。故保留 messageCollector 并抑制弃用，
     * 待官方开放 sourceless ERROR 的公开 DSL 后迁移。
     */
    @Suppress("DEPRECATION")
    private fun IrPluginContext.reportError(message: String) {
        messageCollector.report(CompilerMessageSeverity.ERROR, message)
    }

    /** 后缀直接取 Kotlin 类型 simpleName，与库的后端函数名 persistedGet<Int/Long/...> 一一对应 */
    private fun typeSuffix(type: IrType, context: IrPluginContext, name: String): String? = when {
        type.isInt() -> Int::class.simpleName
        type.isLong() -> Long::class.simpleName
        type.isFloat() -> Float::class.simpleName
        type.isBoolean() -> Boolean::class.simpleName
        type.isString() -> String::class.simpleName
        else -> {
            context.reportError("[@Persisted] 不支持的属性类型 '$name'：仅支持 Int/Long/Float/Boolean/String")
            null
        }
    }

    /**
     * reset/resetScope 调用点重写：把属性/类引用换成编译期算好的 key 字面量。
     * 仅匹配 PersistedStore 的两个引用型重载（字符串/谓词重载因参数形态不同自然绕过）
     */
    private inner class ResetCallRewriter(private val context: IrPluginContext) : IrElementTransformerVoid() {

        override fun visitCall(expression: IrCall): IrExpression {
            expression.transformChildren(this, null)
            // PersistedStore 来自依赖库（反序列化 IR），parent 不是本模块 IrFile，
            // 故按「函数名 + 声明类名 + 参数类型」匹配引用型重载；字符串/谓词重载参数形态不同自然绕过
            val fn = expression.symbol.owner as? IrSimpleFunction ?: return expression
            // 2.x IR 的 arguments 含 dispatch receiver（[0] 是 PersistedStore 实例），引用参数在末位
            val arg = expression.arguments.lastOrNull() ?: return expression
            // 成员函数的 parameters 含 dispatch receiver，末位即引用参数
            val paramType = fn.parameters.last().type.classFqName?.asString()
            return when {
                fn.name.asString() == "reset" &&
                    fn.parentAsClass.name.asString() == "PersistedStore" &&
                    paramType == "kotlin.reflect.KProperty0" -> rewriteReset(expression, arg)
                fn.name.asString() == "resetScope" &&
                    fn.parentAsClass.name.asString() == "PersistedStore" &&
                    paramType == "kotlin.reflect.KClass" -> rewriteResetScope(expression, arg)
                else -> expression
            }
        }

        private fun rewriteReset(call: IrCall, arg: IrExpression): IrExpression {
            val ref = arg as? IrPropertyReference ?: return call
            val getterFn = ref.getter?.owner
            val property = getterFn?.correspondingPropertySymbol?.owner
            if (property == null) {
                context.reportError("[@Persisted] reset 引用解析不到对应属性")
                return call
            }
            // 注解可能挂在属性或其 getter 之一（取决于符号具体化路径），两处都查；
            // 未标注的属性只要处于持久化作用域内同样合法（作用域自动纳入）
            val annotation = property.annotations.firstOrNull { it.isPersistedAnnotation() }
                ?: getterFn.annotations.firstOrNull { it.isPersistedAnnotation() }
            val name = property.name.asString()
            val scope = fullScope(property.parentAsClass)
            if (annotation == null && scope == null) {
                context.reportError("[@Persisted] reset 引用的 '$name' 不是 @Persisted 属性且不在作用域内")
                return call
            }
            val key = computeKey(property, annotation, scope, name, context) ?: return call
            val fn = context.findBackendFunction(RESET_KEY_FN_NAME) ?: return failMissing(call, RESET_KEY_FN_NAME)
            return buildCall(call, fn, key)
        }

        private fun rewriteResetScope(call: IrCall, arg: IrExpression): IrExpression {
            // `X::class` 的表达式类型是 KClass<X>，被引用类在第一个类型实参里；
            // 嵌套类的 FqName 需要「包 + 相对路径」切分，逐个切分点尝试解析
            val classRef = arg as? IrClassReference ?: return call
            val classType = (classRef.type as? IrSimpleType)?.arguments?.firstOrNull()?.typeOrNull
                as? IrSimpleType ?: return call
            val fqName = classType.classFqName ?: return call
            val irClass = resolveClass(fqName) ?: return call
            val scope = fullScope(irClass)
            if (scope == null) {
                context.reportError(
                    "[@Persisted] resetScope 目标不是持久化作用域：'${irClass.name.asString()}' " +
                        "未标注 @Persisted 且不在作用域内"
                )
                return call
            }
            val fn = context.findBackendFunction(RESET_SCOPE_KEY_FN_NAME) ?: return failMissing(call, RESET_SCOPE_KEY_FN_NAME)
            return buildCall(call, fn, scope)
        }

        /** 沿声明容器向上累计作用域段（复用 processClass 的同一条规则） */
        private fun fullScope(irClass: IrClass): String? =
            scopeSegment(irClass, (irClass.parent as? IrClass)?.let { fullScope(it) })

        /** FqName 解析为 IrClass：嵌套类的 ClassId 是「包 + 相对路径」，逐个切分点尝试 */
        private fun resolveClass(fqName: FqName): IrClass? {
            val segments = fqName.pathSegments().map { it.asString() }
            for (i in 0 until segments.size - 1) {
                val pkg = FqName.fromSegments(segments.subList(0, i + 1))
                val relative = FqName.fromSegments(segments.subList(i + 1, segments.size))
                context.referenceClass(ClassId(pkg, relative, false))?.owner?.let { return it }
            }
            return null
        }

        private fun failMissing(call: IrCall, fnName: String): IrExpression {
            context.reportError("[@Persisted] 找不到库函数 $fnName（datastore-extensions 版本过旧？）")
            return call
        }

        private fun buildCall(original: IrCall, fn: IrSimpleFunctionSymbol, argValue: String): IrExpression {
            val builder = IrSingleStatementBuilder(
                context, Scope(original.symbol), original.startOffset, original.endOffset, IrStatementOrigin.INVOKE,
            )
            val call = builder.irCall(fn, context.irBuiltIns.unitType, 1, IrStatementOrigin.INVOKE)
            call.arguments[0] = builder.irString(argValue)
            return call
        }
    }
}
