package org.luaj.luajc

import SerializableObjects.SerializableExecutionLuaStack
import luaClosure
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlinx.coroutines.*

class CoreTest()  : Serializable {
    suspend fun coreFun(){
        println("start delay")
        coreDelay(500)
        println("end delay")
        val executionStack = luaClosure?.getExecutionContext()
        File("context").writeBytes(serializeExecutionContext(executionStack))
        luaClosure?.stop()
        luaClosure = null
    }

    suspend fun coreDelay(msec: Long){
        println("start delay $msec")
        delay(msec)
        println("end delay")
    }

    private fun serializeExecutionContext(executionStack: SerializableExecutionLuaStack?): ByteArray {
        executionStack!!.setJavaLevel(executionStack.getCurrentLevel())
        executionStack.setCurrentLevel(0)
        val byteOutputStream = ByteArrayOutputStream()
        ObjectOutputStream(byteOutputStream).use { oos -> oos.writeObject(luaClosure) }
        return byteOutputStream.toByteArray()
    }
}
