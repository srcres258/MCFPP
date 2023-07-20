package top.mcfpp.lib

import org.jetbrains.annotations.Nullable
import top.mcfpp.lang.Var
import java.util.HashMap

class FunctionField :IField {

    /**
     * 变量
     */
    private val vars: HashMap<String, Var> = HashMap()

    /**
     * 父级域。函数的父级域可能是全局，也可能是类
     */
    @Nullable
    var parent : IField?

    /**
     * 这个缓存在哪一个容器中
     */
    @Nullable
    var container: FieldContainer? = GlobalField

    /**
     * 创建一个缓存，并指定它的父级
     * @param parent 父级缓存。若没有则设置为null
     * @param cacheContainer 此缓存所在的容器
     */
    constructor(parent: IField?, cacheContainer: FieldContainer?) {
        this.parent = parent
        container = cacheContainer
    }

    /**
     * 复制一个域。
     * @param functionField 原来的域
     */
    constructor(functionField: FunctionField) {
        parent = functionField.parent
        //变量复制
        for (key in functionField.vars.keys) {
            val `var`: Var? = functionField.vars[key]
            vars[key] = `var`!!.clone() as Var
        }
    }

    //region Var
    /**
     * 向此缓存中添加一个新的变量键值对。如果已存在此对象，将不会进行覆盖。
     * @param key 变量的标识符
     * @param var 变量的对象
     * @return 如果缓存中已经存在此对象，则返回false，否则返回true。
     */
    fun putVar(key: String, `var`: Var): Boolean {
        return if (vars.containsKey(key)) {
            false
        } else {
            vars[key] = `var`
            true
        }
    }

    /**
     * 从缓存中取出一个变量。如果此缓存中没有，则从父缓存中寻找。
     * @param key 变量的标识符
     * @return 变量的对象。若不存在，则返回null。
     */
    fun getVar(key: String): Var? {
        return vars.getOrDefault(key, null)
    }


    val allVars: Collection<Var>
        /**
         * 获取此缓存中的全部变量。不会从父缓存搜索。
         * @return 一个包含了此缓存全部变量的集合。
         */
        get() = vars.values

    /**
     * 缓存中是否包含某个变量
     * @param id 变量名
     * @return 如果包含则返回true，否则返回false
     */
    fun containVar(id: String): Boolean {
        return vars.containsKey(id)
    }

    /**
     * 移除缓存中的某个变量
     *
     * @param id 变量名
     * @return 若变量存在，则返回被移除的变量，否则返回空
     */
    fun removeVar(id : String): Var?{
        return vars.remove(id)
    }

//endregion

}