package top.mcfpp.lang;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.InsertCommand;
import top.mcfpp.lang.*;
import top.mcfpp.lib.Function;

public class System {
    public static void print(@NotNull Var[] vars, ClassPointer cls) {
        //ֻ����һ������Ŷ
        Var var = vars[0];
        if (var instanceof MCInt) print((MCInt) var);
        else if (var instanceof JsonString) print((JsonString) var);
        else print(var);
    }

    @InsertCommand
    public static void print(@NotNull MCInt var) {
        if (var.isConcrete()) {
            //��ȷ���ģ�ֱ�������ֵ
            Function.Companion.addCommand("tellraw @a " + var.getValue());
        }else {
            Function.Companion.addCommand("tellraw @a " + new JsonTextNumber(var).toJson());
        }
    }

    @InsertCommand
    public static void print(JsonString var){
        Function.Companion.addCommand("tellraw @a " + var.getJsonText().toJson());
    }

    @InsertCommand
    public static void print(@NotNull Var var){
        Function.Companion.addCommand("tellraw @a " + "\"" +var + "\"");
    }

    public static void test(Var[] vars,  ClassPointer cls){
        java.lang.System.out.println("qwq");
    }
}