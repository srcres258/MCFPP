package top.mcfpp.lib.generic

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.Project
import top.mcfpp.antlr.McfppFieldVisitor
import top.mcfpp.antlr.McfppGenericClassVisitor
import top.mcfpp.io.McfppFile
import top.mcfpp.lang.MCAny
import top.mcfpp.lang.MCFPPTypeVar
import top.mcfpp.lang.Var
import top.mcfpp.lang.type.MCFPPGenericClassType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.Class
import top.mcfpp.lib.CompoundData
import top.mcfpp.lib.field.GlobalField
import top.mcfpp.lib.field.SimpleFieldWithVar
import top.mcfpp.lib.function.Constructor
import top.mcfpp.lib.function.Function
import top.mcfpp.lib.function.FunctionParam
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper

class GenericClass : Class {

    val ctx : mcfppParser.ClassBodyContext

    val compiledClasses: HashMap<ArrayList<Var<*>>, Class> = HashMap()

    var index = 0

    val readOnlyParams: ArrayList<ClassParam> = ArrayList()

    /**
     * 生成一个类，它拥有指定的标识符和命名空间
     * @param identifier 类的标识符
     * @param namespace 类的命名空间
     */
    constructor(identifier: String, namespace: String = Project.currNamespace, ctx : mcfppParser.ClassBodyContext):super(identifier, namespace) {
        this.ctx = ctx
    }

    fun compile(readOnlyArgs: ArrayList<Var<*>>) : Class{
        val cls = Class(identifier + "_" + index, namespace)
        cls.initialize()
        for (parent in this.parent){
            cls.extends(parent)
        }
        cls.isStaticClass = this.isStaticClass
        cls.isAbstract = this.isStaticClass

        //只读属性
        for (i in readOnlyParams.indices) {
            val r = readOnlyArgs[i].clone()
            r.isConst = true
            if(r is MCFPPTypeVar){
                cls.field.putType(readOnlyParams[i].identifier, r.javaValue!!)
            }
            cls.field.putVar(readOnlyParams[i].identifier, r, false)
        }

        //注册
        val field = GlobalField.localNamespaces[namespace]!!
        field.addClass(cls.identifier, cls)
        Class.currClass = cls

        val visitor = McfppGenericClassVisitor(cls)
        visitor.visitClassDeclaration(ctx.parent as mcfppParser.ClassDeclarationContext)
        index ++

        cls.getType = {cls.getType().getGenericClassType(readOnlyArgs)}

        return cls
    }
}

class ClassParam(

    /**
     * 参数类型
     */
    var typeIdentifier: String,

    /**
     * 参数的名字
     */
    var identifier: String,
){

}