import org.luaj.luajc.CoreTest
import org.luaj.vm2.LuaClosure
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.ByteArrayInputStream
import java.io.File
import java.io.ObjectInputStream

class MainT {
}

var script =
    "function aa()\n" +
            "	bb()\n"+
            "   print(\"hello0\")\n"+
            "end\n"+
            "function cc()\n" +
            "	aa()\n"+
            "   print(\"hello1\")\n"+
            "end\n"+
            "function bb()\n" +
            "	local a=obj:TestTest()\n"+
            "	print(a)\n"+
            "end\n"+
            "cc()\n"+
            "print(\"hello1337\")\n"

fun main() {
    executeNewProcedure()
    executeOldProcedureFromExecutionContext()
}

fun executeOldProcedureFromExecutionContext(){
    luaClosure = deserializeExecutionContext(File("filename1.txt").readBytes())
    luaClosure!!.setReturnValue("teeeeeeeeeest")
    luaClosure!!.call()
}

fun executeNewProcedure(){
    val globals = JsePlatform.standardGlobals()
    luaClosure = globals.load(script, "script") as LuaClosure
    val test = CoerceJavaToLua.coerce(CoreTest())
    globals["obj"] = test
    luaClosure?.call()
}

fun deserializeExecutionContext(executionContext: ByteArray): LuaClosure {
    val bis = ByteArrayInputStream(executionContext)
    ObjectInputStream(bis).use { ois -> return ois.readObject() as LuaClosure }
}

var luaClosure: LuaClosure? = null