import kotlinx.coroutines.*
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
    "function test(a1,b1,c1,d1,e1)\n" +
    "	print(a1..b1..c1..d1..e1)\n"+
    "   local a=obj:TestTest()\n"+
    "	print(a)\n"+
    "   print(\"end of test\")\n"+
    "end\n"+
    "function test2(a1,b1,c1,d1)\n"+
    "	print(a1..b1..c1..d1)\n"+
    "   test(1,2,3,4,5)\n"+
    "   local a3=obj:TestTest(1)\n"+
    "	print(a3)\n"+
    "   print(\"end of test2\")\n"+
    "end\n"+
    "function test3(a1,b1,c1)\n"+
    "	print(a1..b1..c1)\n"+
    "   local a=obj:TestTest()\n"+
    "	print(a)\n"+
    "   test2(1,2,3,4)\n"+
    "   print(\"end of test3\")\n"+
    "end\n"+
    "function test4(a1, b1)\n"+
    "	print(a1..b1)\n"+
    "   local a=obj:TestTest()\n"+
    "	print(a)\n"+
    "   test3(1,2,3)\n"+
    "   local a2=obj:TestTest()\n"+
    "	print(a2)\n"+
    "   print(\"end of test4\")\n"+
    "end\n"+
    "test4(1, 2)"+
    "   local a2=obj:TestTest()\n"+
    "	print(a2)\n"+
"   print(\"end of script\")\n"

var script2 =
    "function test(a1,b1,c1,d1,e1,f1,g1,h1,i1,j1,k1,l1,m1)\n" +
            "	print(a1..b1..c1..d1..e1..f1..g1..h1..i1..j1..k1..l1..m1)\n"+
            "   local a=obj:TestTest()\n"+
            "	print(a)\n"+
            "   print(\"end of test\")\n"+
            "test(1,2,3,4,5,6,7,8,9,10,11,12,13)\n"+
            "end\n"+
            "test(1,2,3,4,5,6,7,8,9,10,11,12,13)"

suspend fun main()= coroutineScope{
    launch{
        executeNewProcedure()
       // delay(1000)
        executeOldProcedureFromExecutionContext()
        executeOldProcedureFromExecutionContext()
        executeOldProcedureFromExecutionContext()
        executeOldProcedureFromExecutionContext()
        executeOldProcedureFromExecutionContext()
        executeOldProcedureFromExecutionContext()
    }

    println("Start")
}

suspend fun executeOldProcedureFromExecutionContext(){
    luaClosure = deserializeExecutionContext(File("filename1.txt").readBytes())
    luaClosure!!.setReturnValue("teeeeeeeeeest")
    luaClosure!!.callSuspend()
}

suspend fun executeNewProcedure(){
    val globals = JsePlatform.standardGlobals()
    luaClosure = globals.load(script, "script") as LuaClosure
    val test = CoerceJavaToLua.coerce(CoreTest())
    globals["obj"] = test
    luaClosure?.callSuspend()
}

fun deserializeExecutionContext(executionContext: ByteArray): LuaClosure {
    val bis = ByteArrayInputStream(executionContext)
    ObjectInputStream(bis).use { ois -> return ois.readObject() as LuaClosure }
}

var luaClosure: LuaClosure? = null