@file:OptIn(ExperimentalCompilerApi::class, UnsafeDuringIrConstructionAPI::class)

package io.github.lijoeallen.kk.persist

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
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
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

const val PLUGIN_ID = "io.github.lijoeallen.kk.persist"

private const val ANNOTATION_FQ_NAME = "io.github.lijoeallen.kk.K"
private const val BACKEND_PACKAGE = "io.github.lijoeallen.kk"

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
 * IR 阶段重写 @K 注解属性（无委托）的访问器：
 *   getter → return persistedGet<Type>("<属性名>", <backingField>)
 *   setter → persistedSet<Type>("<属性名>", value)
 * 已带委托（by persisted(...)）的属性保持原样，两种写法并存。
 */
class PersistIrGenerationExtension : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.files.forEach { file ->
            file.declarations.filterIsInstance<IrProperty>().forEach { transform(it, pluginContext) }
            file.declarations.filterIsInstance<IrClass>().forEach { irClass ->
                irClass.declarations.filterIsInstance<IrProperty>().forEach { transform(it, pluginContext) }
            }
        }
    }

    private fun transform(property: IrProperty, context: IrPluginContext) {
        if (property.isDelegated) return
        if (property.annotations.none { it.type.classFqName?.asString() == ANNOTATION_FQ_NAME }) return
        val field = property.backingField ?: return
        val getter = property.getter ?: return
        val name = property.name.asString()
        val start = property.startOffset
        val end = property.endOffset

        val suffix = typeSuffix(getter.returnType, context, name) ?: return
        val getFun = context.referenceFunctions(CallableId(FqName(BACKEND_PACKAGE), Name.identifier("persistedGet$suffix")))
            .firstOrNull()
        if (getFun == null) {
            context.reportError("[@K] 找不到后端函数 persistedGet$suffix（属性 '$name'）")
            return
        }

        // getter: return persistedGet<Type>("name", <field>)
        val getBuilder = IrSingleStatementBuilder(context, Scope(getter.symbol), start, end, IrStatementOrigin.INVOKE)
        val defaultExpr = getBuilder.irGetField(
            getter.dispatchReceiverParameter?.let { getBuilder.irGet(it) },
            field,
            field.type,
        )
        val getCall = getBuilder.irCall(getFun, getter.returnType, 2, IrStatementOrigin.INVOKE)
        getCall.arguments[0] = getBuilder.irString(name)
        getCall.arguments[1] = defaultExpr
        getter.body = getBuilder.irBlockBody(start, end) {
            +IrReturnImpl(start, end, context.irBuiltIns.nothingType, getter.symbol, getCall)
        }

        // setter: persistedSet<Type>("name", value)
        val setter = property.setter ?: return
        val setFun = context.referenceFunctions(CallableId(FqName(BACKEND_PACKAGE), Name.identifier("persistedSet$suffix")))
            .firstOrNull()
        if (setFun == null) {
            context.reportError("[@K] 找不到后端函数 persistedSet$suffix（属性 '$name'）")
            return
        }
        val setBuilder = IrSingleStatementBuilder(context, Scope(setter.symbol), start, end, IrStatementOrigin.INVOKE)
        val setCall = setBuilder.irCall(setFun, setter.returnType, 2, IrStatementOrigin.INVOKE)
        setCall.arguments[0] = setBuilder.irString(name)
        setCall.arguments[1] = setBuilder.irGet(setter.parameters.last())
        setter.body = setBuilder.irBlockBody(start, end) { +setCall }
    }

    private fun IrPluginContext.reportError(message: String) {
        messageCollector.report(CompilerMessageSeverity.ERROR, message)
    }

    private fun typeSuffix(type: IrType, context: IrPluginContext, name: String): String? = when {
        type.isInt() -> "Int"
        type.isLong() -> "Long"
        type.isFloat() -> "Float"
        type.isBoolean() -> "Boolean"
        type.isString() -> "String"
        else -> {
            context.reportError("[@K] 不支持的属性类型 '$name'：仅支持 Int/Long/Float/Boolean/String")
            null
        }
    }
}
