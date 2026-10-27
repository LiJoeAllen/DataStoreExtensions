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
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

const val PLUGIN_ID = "io.github.lijoeallen.datastoreextensions.persist"

private const val ANNOTATION_FQ_NAME = "io.github.lijoeallen.datastoreextensions.Persisted"
private const val BACKEND_PACKAGE = "io.github.lijoeallen.datastoreextensions"

// key 段之间的分隔符（存储契约的一部分，与库文档一致）
private const val KEY_SEPARATOR = "-"

// 注解参数名（与 Persisted.kt 的 annotation class Persisted(key) 一一对应）
private const val PARAM_KEY = "key"

// 库的后端函数命名（与 Persisted.kt 中的函数一一对应，勿改名）
private const val GETTER_FN_PREFIX = "persistedGet"
private const val SETTER_FN_PREFIX = "persistedSet"
private const val PERSISTED_FN_NAME = "persisted"

/**
 * 按 CallableId 查找后端函数符号（位于依赖模块 datastore-extensions 中）。
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
 * IR 阶段重写 @Persisted 属性的访问器：
 *   getter → return persistedGet<Type>("<key>", <默认值>)
 *   setter → persistedSet<Type>("<key>", value)
 *
 * key = 各层 key 段以 "-" 连接（多级作用域逐层追加）：
 *   - 属性段 = 属性 @Persisted 的 key（缺省为属性名）
 *   - 对象段 = 对象 @Persisted 的 key（缺省为对象简单名），嵌套时拼接外层作用域
 *   - 属性 key 以 "/" 开头为绝对 key：忽略全部作用域段（存量 flat key 收拢零迁移）
 *
 * @Persisted object 是持久化作用域：作用域内所有 var 与嵌套对象自动纳入；
 * 作用域外（顶层/普通类）仅带 @Persisted 的成员持久化（无作用域前缀）。
 *
 * 两种声明写法均重写：`var abc = 100`（默认值取 backing field）、
 * `var xyz by persisted(1)`（默认值取 persisted(字面量) 参数）；
 * 非 persisted() 的自定义委托保持运行时委托语义，不动。
 */
class PersistIrGenerationExtension : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.files.forEach { file ->
            file.declarations.forEach { declaration ->
                when (declaration) {
                    is IrProperty -> if (hasPersisted(declaration)) transform(declaration, pluginContext, scope = null)
                    is IrClass -> processClass(declaration, pluginContext, parentScope = null)
                    else -> Unit
                }
            }
        }
    }

    /** 递归处理类及其嵌套类：scope != null 表示处于持久化作用域内（全部成员自动纳入） */
    private fun processClass(irClass: IrClass, context: IrPluginContext, parentScope: String?) {
        val scope = scopeSegment(irClass, parentScope)
        irClass.declarations.forEach { declaration ->
            when (declaration) {
                is IrProperty -> when {
                    scope != null -> transform(declaration, context, scope)
                    hasPersisted(declaration) -> transform(declaration, context, null)
                }
                is IrClass -> processClass(declaration, context, scope)
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

    private fun transform(property: IrProperty, context: IrPluginContext, scope: String?) {
        val getter = property.getter ?: return
        val name = property.name.asString()
        val start = property.startOffset
        val end = property.endOffset

        // key = 各层 key 段拼接：作用域段（可多层）+ 属性段；
        // 属性段以 "/" 开头为绝对 key：忽略全部作用域段，用于存量 flat key 收拢与迁移
        val annotation = property.annotations.firstOrNull { it.isPersistedAnnotation() }
        val raw = annotation?.paramValue(PARAM_KEY)?.takeIf { it.isNotEmpty() }
        val key = when {
            raw != null && raw.startsWith("/") -> {
                val absolute = raw.substring(1)
                if (absolute.isEmpty()) {
                    context.reportError("[@Persisted] 绝对 key（\"/\" 开头）不能为空（属性 '$name'）")
                    return
                }
                absolute
            }
            else -> {
                val segment = raw ?: name
                scope?.let { "$it$KEY_SEPARATOR$segment" } ?: segment
            }
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
}
