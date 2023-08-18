package top.mcfpp.lib.antlr

import mcfppBaseListener
import org.antlr.v4.runtime.RuleContext
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.exception.*
import top.mcfpp.lang.*
import top.mcfpp.lib.*
import top.mcfpp.lib.Function

class McfppImListener : mcfppBaseListener() {

    /**
     * 完成一次库的import
     *
     * @param ctx
     */
    override fun exitImportDeclaration(ctx: mcfppParser.ImportDeclarationContext?) {
        Project.ctx = ctx
        //获取命名空间
        var namespace = ctx!!.Identifier(0).text
        if (ctx.Identifier().size > 1) {
            for (n in ctx.Identifier().subList(1, ctx.Identifier().size - 1)) {
                namespace += ".$n"
            }
        }
        //获取库的命名空间
        val libNamespace = GlobalField.libNamespaces[namespace]
        if (libNamespace == null) {
            Project.error("Namespace $namespace not found")
            throw NamespaceNotFoundException()
        }
        //将库的命名空间加入到importedLibNamespaces中
        val nsp = NamespaceField()
        GlobalField.importedLibNamespaces[namespace] = nsp

        //这个库被引用的类
        if(ctx.cls == null){
            //只导入方法
            libNamespace.forEachFunction { f ->
                run {
                    nsp.addFunction(f)
                }
            }
            return
        }
        //导入类和方法
        if(ctx.cls.text == "*"){
            //全部导入
            libNamespace.forEachClass { c ->
                run {
                    nsp.addClass(c.identifier,c)
                }
            }
            libNamespace.forEachFunction { f ->
                run {
                    nsp.addFunction(f)
                }
            }
        }else{
            //只导入声明的类
            val cls = libNamespace.getClass(ctx.cls.text)
            if(cls != null){
                nsp.addClass(cls.identifier,cls)
            }else{
                Project.error("Class ${ctx.cls.text} not found in namespace $namespace")
                throw ClassNotDefineException()
            }
        }
    }

    /**
     * 进入一个函数体
     * @param ctx the parse tree
     */
    @Override
    override fun enterFunctionBody(ctx: mcfppParser.FunctionBodyContext) {
        Project.ctx = ctx
        var f: Function
        //获取函数对象
        if (ctx.parent.parent !is mcfppParser.ClassMemberContext && ctx.parent.parent !is mcfppParser.StructMemberContext) {
            //不是类成员和结构体成员
            //创建函数对象
            val parent = ctx.parent as mcfppParser.FunctionDeclarationContext
            f = Function(parent.Identifier().text)
            //解析参数
            if (parent.parameterList() != null) {
                f.addParams((ctx.parent as mcfppParser.FunctionDeclarationContext).parameterList())
            }
            //获取缓存中的对象
            f = GlobalField.getFunction(f.namespace, f.name, f.paramTypeList)!!
        } else if (ctx.parent is mcfppParser.ConstructorDeclarationContext) {
            //是构造函数
            //创建构造函数对象并解析参数
            val temp = Function("temp")
            if ((ctx.parent as mcfppParser.ConstructorDeclarationContext).parameterList() != null) {
                temp.addParams((ctx.parent as mcfppParser.ConstructorDeclarationContext).parameterList())
            }
            //获取缓存中的对象
            f = if(ctx.parent.parent is mcfppParser.ClassMemberContext){
                Class.currClass!!.getConstructor(FunctionParam.toStringList(temp.params))!!
            }else{
                Struct.currStruct!!.getConstructor(FunctionParam.toStringList(temp.params))!!
            }
        } else if(ctx.parent is mcfppParser.ClassMemberContext){
            //是类的成员函数
            //创建函数对象并解析参数
            val qwq = ctx.parent as mcfppParser.ClassFunctionDeclarationContext
            f = Function(qwq.Identifier().text, Class.currClass!!, false)
            if (qwq.parameterList() != null) {
                f.addParams(qwq.parameterList())
            }
            //获取缓存中的对象
            val fun1 = Class.currClass!!.field.getFunction(f.name, f.paramTypeList)
            f = (fun1 ?: Class.currClass!!.staticField.getFunction(f.name, f.paramTypeList))!!
        }else{
            //是结构体成员
            //创建函数对象并解析参数
            val qwq = ctx.parent as mcfppParser.StructFunctionDeclarationContext
            f = Function(qwq.Identifier().text, Struct.currStruct!!, false)
            if (qwq.parameterList() != null) {
                f.addParams(qwq.parameterList())
            }
            //获取缓存中的对象
            val fun1 = Struct.currStruct!!.field.getFunction(f.name, f.paramTypeList)
            f = (fun1 ?: Struct.currStruct!!.staticField.getFunction(f.name, f.paramTypeList))!!
        }
        Function.currFunction = f
    }

    /**
     * 离开一个函数体
     * @param ctx the parse tree
     */
    @Override
    override fun exitFunctionBody(ctx: mcfppParser.FunctionBodyContext?) {
        Project.ctx = ctx
        //函数是否有返回值
        if(Function.currFunction.returnType != "void" && !Function.currFunction.hasReturnStatement){
            Project.error("A 'return' expression required in function: " + Function.currFunction.namespaceID)
        }
        if (Class.currClass == null) {
            //不在类中
            Function.currFunction = Function.nullFunction
        } else {
            Function.currFunction = Class.currClass!!.classPreInit
        }
    }

    /**
     * 进入命名空间声明的时候
     * @param ctx the parse tree
     */
    @Override
    override fun exitNamespaceDeclaration(ctx: mcfppParser.NamespaceDeclarationContext) {
        Project.ctx = ctx
        Project.currNamespace = ctx.Identifier(0).text
        if(ctx.Identifier().size > 1){
            for (n in ctx.Identifier().subList(1,ctx.Identifier().size-1)){
                Project.currNamespace += ".$n"
            }
        }
    }

    /**
     * 变量声明
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun exitFieldDeclaration(ctx: mcfppParser.FieldDeclarationContext) {
        Project.ctx = ctx
        //变量生成
        val `var`: Var = if (ctx.parent is mcfppParser.ClassMemberContext) {
            return
        } else {
            //函数变量，生成
            Var.build(ctx, Function.currFunction)!!
        }
        //变量注册
        //一定是函数变量
        if (!Function.currFunction.field.putVar(ctx.Identifier().text, `var`)) {
            Project.error("Duplicate defined variable name:" + ctx.Identifier().text)
            throw VariableDuplicationException()
        }
        Function.addCommand("#" + ctx.type().text + " " + ctx.Identifier().text + if (ctx.expression() != null) " = " + ctx.expression().text else "")
        //变量初始化
        if (ctx.expression() != null) {
            val init: Var = McfppExprVisitor().visit(ctx.expression())!!
            try {
                `var`.assign(init)
            } catch (e: VariableConverseException) {
                Project.error("Cannot convert " + init.javaClass + " to " + `var`.javaClass)
                throw VariableConverseException()
            }
        }
    }

    /**
     * 一个赋值的语句
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun exitStatementExpression(ctx: mcfppParser.StatementExpressionContext) {
        Project.ctx = ctx
        Function.addCommand("#" + ctx.text)
        val left: Var = McfppExprVisitor().visit(ctx.basicExpression())!!
        if (left.isConst == Var.ConstStatus.ASSIGNED) {
            Project.error("Cannot assign a constant repeatedly: " + left.identifier)
            throw ConstChangeException()
        } else if (left.isConst == Var.ConstStatus.NULL) {
            left.isConst = Var.ConstStatus.ASSIGNED
        }
        val right: Var = McfppExprVisitor().visit(ctx.expression())!!
        try {
            left.assign(right)
        } catch (e: VariableConverseException) {
            Project.error("Cannot convert " + right.javaClass + " to " + left.javaClass)
            throw VariableConverseException()
        }
    }

    /**
     * 自加或自减语句
     * TODO
     * @param ctx the parse tree
     */
    /*@Override
    * override fun exitSelfAddOrMinusStatement(ctx: mcfppParser.SelfAddOrMinusStatementContext) {
    *    Project.ctx = ctx
    *    Function.addCommand("#" + ctx.text)
    *    val re: Var? = Function.currFunction.field.getVar(ctx.selfAddOrMinusExpression().Identifier().text)
    *    if (re == null) {
    *        Project.error("Undefined variable:" + ctx.selfAddOrMinusExpression().Identifier().text)
    *        throw VariableNotDefineException()
    *    }
    *    if (ctx.selfAddOrMinusExpression().op.text.equals("++")) {
    *        if (re is MCInt) {
    *            if (re.isConcrete) {
    *                re.value = re.value!! + 1
    *            } else {
    *                Function.addCommand(Commands.SbPlayerAdd(re, 1))
    *            }
    *        }
    *    } else {
    *        if (re is MCInt) {
    *            if (re.isConcrete) {
    *                re.value = re.value!! - 1
    *            } else {
    *                Function.addCommand(Commands.SbPlayerRemove(re, 1))
    *            }
    *        }
    *    }
    * }
    */

    /**
     * 调用一个函数。参考：
     * [局部变量、程序控制流程在数据包中的实现 借助汇编语言函数堆栈思想 - mcbbs](https://www.mcbbs.net/thread-1393132-1-1.html)
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun exitFunctionCall(ctx: mcfppParser.FunctionCallContext) {
        Project.ctx = ctx
        Function.addCommand("#" + ctx.text)
        //参数获取
        val args: ArrayList<Var> = ArrayList()
        val exprVisitor = McfppExprVisitor()
        if(ctx.arguments().expressionList() != null){
            for (expr in ctx.arguments().expressionList().expression()) {
                args.add(exprVisitor.visit(expr)!!)
            }
        }
        //函数对象获取
        val curr = McfppFuncVisitor().getFunction(ctx, FunctionParam.getVarTypes(args))
        val func = curr.first
        val obj = curr.second
        if (func == null) {
            Project.error("Function " + ctx.text + " not defined")
            throw FunctionNotDefineException()
        }
        func.invoke(args,obj)
        //函数树
        Function.currFunction.child.add(func)
        func.parent.add(Function.currFunction)
    }

    @Override
    @InsertCommand
    override fun exitReturnStatement(ctx: mcfppParser.ReturnStatementContext?) {
        Project.ctx = ctx
        Function.addCommand("#" + ctx!!.text)
        if (ctx.expression() != null) {
            if(Function.currBaseFunction.returnType == "void"){
                Project.error("Function ${Function.currBaseFunction.name} has no return value")
                throw FunctionHasNoReturnValueException()
            }
            val ret: Var = McfppExprVisitor().visit(ctx.expression())!!
            try {
                Function.currBaseFunction.returnVar!!.assign(ret)
            } catch (e: VariableConverseException) {
                Project.error("Cannot convert " + ret.javaClass + " to " + Function.currBaseFunction.returnVar!!.javaClass)
                throw VariableConverseException()
            }
        }
        if(Function.currFunction !is InternalFunction)
            Function.currFunction.hasReturnStatement = true
        Function.addCommand("return 1")
    }

    //region 逻辑语句
    /**
     * 进入if语句
     * Enter if statement
     *
     * @param ctx
     */
    @Override
    @InsertCommand
    override fun enterIfStatement(ctx: mcfppParser.IfStatementContext?) {
        //进入if函数
        Project.ctx = ctx
        Function.addCommand("#" + "if start")
        val ifFunction = InternalFunction("_if_", Function.currFunction)
        Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        Function.addCommand("function " + ifFunction.namespaceID)
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = ifFunction
        if(!GlobalField.localNamespaces.containsKey(ifFunction.namespace))
            GlobalField.localNamespaces[ifFunction.namespace] = NamespaceField()
        GlobalField.localNamespaces[ifFunction.namespace]!!.addFunction(ifFunction)
    }

    /**
     * 离开if语句
     * Exit if statement
     *
     * @param ctx
     */
    @Override
    @InsertCommand
    override fun exitIfStatement(ctx: mcfppParser.IfStatementContext?) {
        Project.ctx = ctx
        Function.currFunction = Function.currFunction.parent[0]
        //调用完毕，将子函数的栈销毁
        Function.addCommand("#" + "if end")
    }

    /**
     * 进入if分支的语句块
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun enterIfBlock(ctx: mcfppParser.IfBlockContext) {
        Project.ctx = ctx
        val parent = ctx.parent
        Function.addCommand("#if branch start")
        //匿名函数的定义
        val f = InternalFunction("_if_branch_", Function.currFunction)
        //注册函数
        if(!GlobalField.localNamespaces.containsKey(f.namespace))
            GlobalField.localNamespaces[f.namespace] = NamespaceField()
        GlobalField.localNamespaces[f.namespace]!!.addFunction(f)
        if (parent is mcfppParser.IfStatementContext || parent is mcfppParser.ElseIfStatementContext) {
            //第一个if
            parent as mcfppParser.IfStatementContext
            val exp = McfppExprVisitor().visit(parent.expression())
            if(exp !is MCBool){
                throw TypeCastException()
            }
            if (exp.isConcrete && exp.value) {
                //函数调用的命令
                //给子函数开栈
                Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
                Function.addCommand("function " + f.namespaceID)
                Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
                Function.addCommand("return 1")
                Project.warn("The condition is always true. ")
            } else if (exp.isConcrete) {
                Function.addCommand("#function " + f.namespaceID)
                Function.addCommand("return 1")
                Project.warn("The condition is always false. ")
            } else {
                //给子函数开栈
                Function.addCommand(
                    "execute " +
                            "if score " + exp.name + " " + SbObject.MCS_boolean + " matches 1 " +
                            "run data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}"
                )
                Function.addCommand(
                    "execute " +
                            "if score " + exp.name + " " + SbObject.MCS_boolean + " matches 1 " +
                            "run function " + f.namespaceID
                )
                Function.addCommand(
                    "execute " +
                            "if score " + exp.name + " " + SbObject.MCS_boolean + " matches 1 " +
                            "run data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]"
                )
                Function.addCommand(
                    "execute " +
                            "if score " + exp.name + " " + SbObject.MCS_boolean + " matches 1 " +
                            "run return 1"
                )
            }
        }
        else {
            //else语句
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            Function.addCommand("function " + f.namespaceID)
            Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        }
        Function.currFunction = f
    }

    /**
     * 离开if语句块
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun exitIfBlock(ctx: mcfppParser.IfBlockContext?) {
        Project.ctx = ctx
        Function.currFunction = Function.currFunction.parent[0]
        Function.addCommand("#if branch end")
    }

    @Override
    @InsertCommand
    override fun enterWhileStatement(ctx: mcfppParser.WhileStatementContext?) {
        //进入if函数
        Project.ctx = ctx
        Function.addCommand("#while start")
        val whileFunction = InternalFunction("_while_", Function.currFunction)
        Function.addCommand("function " + whileFunction.namespaceID)
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = whileFunction
        if(!GlobalField.localNamespaces.containsKey(whileFunction.namespace))
            GlobalField.localNamespaces[whileFunction.namespace] = NamespaceField()
        GlobalField.localNamespaces[whileFunction.namespace]!!.addFunction(whileFunction)
    }

    @Override
    @InsertCommand
    override fun exitWhileStatement(ctx: mcfppParser.WhileStatementContext?) {
        Project.ctx = ctx
        Function.currFunction = Function.currFunction.parent[0]
        //调用完毕，将子函数的栈销毁
        Function.addCommand("#while end")
    }

    /**
     * 进入while语句块
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun enterWhileBlock(ctx: mcfppParser.WhileBlockContext) {
        Project.ctx = ctx
        //入栈
        Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        Function.addCommand("#" + "while start")
        val parent: mcfppParser.WhileStatementContext = ctx.parent as mcfppParser.WhileStatementContext
        val exp: MCBool = McfppExprVisitor().visit(parent.expression()) as MCBool
        //匿名函数的定义
        val f: Function = InternalFunction("_while_block_", Function.currFunction)
        f.child.add(f)
        f.parent.add(f)
        if(!GlobalField.localNamespaces.containsKey(f.namespace))
            GlobalField.localNamespaces[f.namespace] = NamespaceField()
        GlobalField.localNamespaces[f.namespace]!!.addFunction(f)
        //条件判断
        if (exp.isConcrete && exp.value) {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "if function " + f.namespaceID + " " +
                        "run function " + Function.currFunction.namespaceID
            )
            Project.warn("The condition is always true. ")
        } else if (exp.isConcrete) {
            //给子函数开栈
            Function.addCommand("#function " + f.namespaceID)
            Project.warn("The condition is always false. ")
        } else {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            //函数返回1才会继续执行(continue或者正常循环完毕)，返回0则不继续循环(break)
            Function.addCommand(
                "execute " +
                        "if score " + exp.name + " " + SbObject.MCS_boolean + " matches 1 " +
                        "if function " + f.namespaceID + " " +
                        "run function " + Function.currFunction.namespaceID
            )
        }
        Function.currFunction = f //后续块中的命令解析到递归的函数中

    }

    /**
     * 离开while语句块
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun exitWhileBlock(ctx: mcfppParser.WhileBlockContext) {
        Project.ctx = ctx
        //调用完毕，将子函数的栈销毁
        //由于在同一个命令中完成了两个函数的调用，因此需要在子函数内部进行子函数栈的销毁工作
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        //这里取出while函数的栈
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = Function.currFunction.parent[0]
        Function.addCommand("#while loop end")
    }

    @Override
    @InsertCommand
    override fun enterDoWhileStatement(ctx: mcfppParser.DoWhileStatementContext?) {
        //进入do-while函数
        Project.ctx = ctx
        Function.addCommand("#do-while start")
        val doWhileFunction = InternalFunction("_dowhile_", Function.currFunction)
        Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        Function.addCommand("function " + doWhileFunction.namespaceID)
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = doWhileFunction
        if(!GlobalField.localNamespaces.containsKey(doWhileFunction.namespace))
            GlobalField.localNamespaces[doWhileFunction.namespace] = NamespaceField()
        GlobalField.localNamespaces[doWhileFunction.namespace]!!.addFunction(doWhileFunction)
    }



    /**
     * 离开do-while语句
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun exitDoWhileStatement(ctx: mcfppParser.DoWhileStatementContext) {
        Project.ctx = ctx
        Function.currFunction = Function.currFunction.parent[0]
        //调用完毕，将子函数的栈销毁
        Function.addCommand("#do-while end")
    }

    /**
     * 进入do-while语句块，开始匿名函数调用
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun enterDoWhileBlock(ctx: mcfppParser.DoWhileBlockContext?) {
        Project.ctx = ctx
        Function.addCommand("#do while start")
        //匿名函数的定义
        val f: Function = InternalFunction("_dowhile_", Function.currFunction)
        f.child.add(f)
        f.parent.add(f)
        if(!GlobalField.localNamespaces.containsKey(f.namespace)) GlobalField.localNamespaces[f.namespace] = NamespaceField()
        GlobalField.localNamespaces[f.namespace]!!.addFunction(f)
        //给子函数开栈
        Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        Function.addCommand(
            "execute " +
                    "unless function " + f.namespaceID + " " +
                    "run return 1"
        )
        val parent = ctx!!.parent as mcfppParser.DoWhileStatementContext
        val exp: MCBool = McfppExprVisitor().visit(parent.expression()) as MCBool
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        //递归调用
        if (exp.isConcrete && exp.value) {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "if function " + f.namespaceID + " " +
                        "run function " + Function.currFunction.namespaceID
            )
            Project.warn("The condition is always true. ")
        } else if (exp.isConcrete) {
            //给子函数开栈
            Function.addCommand("#" + Commands.Function(Function.currFunction))
            Project.warn("The condition is always false. ")
        } else {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "if score " + exp.name + " " + SbObject.MCS_boolean + " matches 1 " +
                        "run " + Commands.Function(Function.currFunction)
            )
        }
        Function.currFunction = f //后续块中的命令解析到递归的函数中
    }

    @Override
    @InsertCommand
    override fun exitDoWhileBlock(ctx: mcfppParser.DoWhileBlockContext?) {
        Project.ctx = ctx
        //调用完毕，将子函数的栈销毁
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        //调用完毕，将子函数的栈销毁
        Function.currFunction = Function.currFunction.parent[0]
        Function.addCommand("#do while end")
    }

    /**
     * 整个for语句本身额外有一个栈，无条件调用函数
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun enterForStatement(ctx: mcfppParser.ForStatementContext?) {
        Project.ctx = ctx
        Function.addCommand("#for start")
        val forFunc: Function = InternalFunction("_for_", Function.currFunction)
        forFunc.parent.add(Function.currFunction)
        if(!GlobalField.localNamespaces.containsKey(forFunc.namespace))
            GlobalField.localNamespaces[forFunc.namespace] = NamespaceField()
        GlobalField.localNamespaces[forFunc.namespace]!!.addFunction(forFunc)
        Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        Function.addCommand(Commands.Function(forFunc))
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = forFunc
    }

    @Override
    @InsertCommand
    override fun exitForStatement(ctx: mcfppParser.ForStatementContext?) {
        Project.ctx = ctx
        Function.currFunction = Function.currFunction.parent[0]
        Function.addCommand("#for end")
    }

    @Override
    @InsertCommand
    override fun enterForInit(ctx: mcfppParser.ForInitContext?) {
        Project.ctx = ctx
        Function.addCommand("#for init start")
    }

    @Override
    @InsertCommand
    override fun exitForInit(ctx: mcfppParser.ForInitContext?) {
        Project.ctx = ctx
        Function.addCommand("#for init end")
        //进入for循环主体
        Function.addCommand("#for loop start")
        val forLoopFunc: Function = InternalFunction("_for_loop_", Function.currFunction)
        forLoopFunc.parent.add(Function.currFunction)
        if(!GlobalField.localNamespaces.containsKey(forLoopFunc.namespace))
            GlobalField.localNamespaces[forLoopFunc.namespace] = NamespaceField()
        GlobalField.localNamespaces[forLoopFunc.namespace]!!.addFunction(forLoopFunc)
        Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        Function.addCommand(Commands.Function(forLoopFunc))

    }

    /**
     * 进入for update语句块。
     * 由于在编译过程中，编译器会首先编译for语句的for control部分，也就是for后面的括号，这就意味着forUpdate语句将会先forBlock
     * 被写入到命令函数中。因此我们需要将forUpdate语句中的命令临时放在一个列表内部，然后在forBlock调用完毕后加上它的命令
     *
     * @param ctx the parse tree
     */
    @Override
    override fun enterForUpdate(ctx: mcfppParser.ForUpdateContext?) {
        Project.ctx = ctx
        forInitCommands = Function.currFunction.commands
        Function.currFunction.commands = forUpdateCommands
    }

    //暂存
    private var forInitCommands = ArrayList<String>()
    private var forUpdateCommands = ArrayList<String>()

    /**
     * 离开for update。暂存for update缓存，恢复主缓存，准备forblock编译
     * @param ctx the parse tree
     */
    @Override
    override fun exitForUpdate(ctx: mcfppParser.ForUpdateContext?) {
        Project.ctx = ctx
        Function.currFunction.commands = forInitCommands
    }

    /**
     * 进入for block语句。此时当前函数为父函数
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun enterForBlock(ctx: mcfppParser.ForBlockContext) {
        Project.ctx = ctx
        val parent: mcfppParser.ForStatementContext = ctx.parent as mcfppParser.ForStatementContext
        val exp: MCBool = McfppExprVisitor().visit(parent.forControl().expression()) as MCBool
        //匿名函数的定义。这里才是正式的for函数哦喵
        val f: Function = InternalFunction("_forblock_", Function.currFunction)
        f.child.add(f)
        f.parent.add(f)
        if(!GlobalField.localNamespaces.containsKey(f.namespace))
            GlobalField.localNamespaces[f.namespace] = NamespaceField()
        GlobalField.localNamespaces[f.namespace]!!.addFunction(f)
        //条件循环判断
        if (exp.isConcrete && exp.value) {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "if function " + f.namespaceID + " " +
                        "run function " + Function.currFunction.namespaceID
            )
            Project.warn("The condition is always true. ")
        } else if (exp.isConcrete) {
            //给子函数开栈
            Function.addCommand("#function " + f.namespaceID)
            Project.warn("The condition is always false. ")
        } else {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            //函数返回1才会继续执行(continue或者正常循环完毕)，返回0则不继续循环(break)
            Function.addCommand(
                "execute " +
                        "if score " + exp.name + " " + SbObject.MCS_boolean + " matches 1 " +
                        "if function " + f.namespaceID + " " +
                        "run function " + Function.currFunction.namespaceID
            )
        }
        //调用完毕，将子函数的栈销毁。这条命令仍然是在for函数中的。
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = f //后续块中的命令解析到递归的函数中
    }

    /**
     * 离开for block语句。此时当前函数仍然是for的函数
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun exitForBlock(ctx: mcfppParser.ForBlockContext) {
        Project.ctx = ctx
        //for update的命令压入
        Function.currFunction.commands.addAll(forUpdateCommands)
        forUpdateCommands.clear()
        //调用完毕，将子函数的栈销毁
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        //继续销毁forloop函数的栈
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = Function.currFunction.parent[0]
    }
//endregion

    @Override
    @InsertCommand
    override fun exitOrgCommand(ctx: mcfppParser.OrgCommandContext) {
        Project.ctx = ctx
        Function.addCommand(ctx.text.substring(1))
    }

    /**
     * 进入任意语句，检查此函数是否还能继续添加语句
     * @param ctx the parse tree
     */
    @Override
    @InsertCommand
    override fun enterStatement(ctx: mcfppParser.StatementContext) {
        Project.ctx = ctx
        if (Function.currFunction.isEnd) {
            Project.warn("Unreachable code: " + ctx.text)
        }
    }

    private var temp: MCBool? = null
    @Override
    @InsertCommand
    override fun exitControlStatement(ctx: mcfppParser.ControlStatementContext) {
        Project.ctx = ctx
        if (!inLoopStatement(ctx)) {
            Project.error("'continue' or 'break' can only be used in loop statements.")
            throw SyntaxException()
        }
        Function.addCommand("#" + ctx.text)
        //return语句
        if(ctx.BREAK() != null){
            Function.addCommand("return 0")
        }else{
            Function.addCommand("return 1")
        }
        Function.currFunction.isEnd = true
    }

    //region class

    /**
     * 进入类体。
     * @param ctx the parse tree
     */
    @Override
    override fun enterClassBody(ctx: mcfppParser.ClassBodyContext) {
        Project.ctx = ctx
        //获取类的对象
        val parent: mcfppParser.ClassDeclarationContext = ctx.parent as mcfppParser.ClassDeclarationContext
        val identifier: String = parent.classWithoutNamespace().text
        //设置作用域
        Class.currClass = GlobalField.getClass(Project.currNamespace, identifier)
        Function.currFunction = Class.currClass!!.classPreInit
    }

    /**
     * 离开类体。将缓存重新指向全局
     * @param ctx the parse tree
     */
    @Override
    override fun exitClassBody(ctx: mcfppParser.ClassBodyContext?) {
        Project.ctx = ctx
        Class.currClass = null
        Function.currFunction = Function.nullFunction
    }

    /**
     * 类成员的声明
     * @param ctx the parse tree
     */
    @Override
    override fun exitClassMemberDeclaration(ctx: mcfppParser.ClassMemberDeclarationContext) {
        Project.ctx = ctx
        val memberContext: mcfppParser.ClassMemberContext = ctx.classMember()
        if (memberContext.classFunctionDeclaration() != null) {
            //函数声明由函数的listener处理
            return
        }
    }

    //endregion

    //struct

    /**
     * 进入类体。
     * @param ctx the parse tree
     */
    @Override
    override fun enterStructBody(ctx: mcfppParser.StructBodyContext) {
        Project.ctx = ctx
        //获取类的对象
        val parent = ctx.parent as mcfppParser.StructDeclarationContext
        val identifier: String = parent.classWithoutNamespace().text
        //设置作用域
        Struct.currStruct = GlobalField.getStruct(Project.currNamespace, identifier)
    }

    /**
     * 离开类体。将缓存重新指向全局
     * @param ctx the parse tree
     */
    @Override
    override fun exitStructBody(ctx: mcfppParser.StructBodyContext?) {
        Project.ctx = ctx
        Struct.currStruct = null
    }

    //endregion


    companion object {
        /**
         * 判断这个语句是否在循环语句中。包括嵌套形式。
         * @param ctx 需要判断的语句
         * @return 是否在嵌套中
         */
        private fun inLoopStatement(ctx: RuleContext): Boolean {
            if (ctx is mcfppParser.ForStatementContext) {
                return true
            }
            if (ctx is mcfppParser.DoWhileStatementContext) {
                return true
            }
            if (ctx is mcfppParser.WhileStatementContext) {
                return true
            }
            return if (ctx.parent != null) {
                inLoopStatement(ctx.parent)
            } else false
        }
    }
}