package org.luaj.luajc

import SerializableObjects.SerializableExecutionLuaStack
import luaClosure
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.io.Serializable

class CoreTest()  : Serializable {
    fun TestTest(){
        val executionStack = luaClosure?.getExecutionContext()
        File("filename1.txt").writeBytes(serializeExecutionContext(executionStack))
        luaClosure?.stop()
        luaClosure = null
    }

    private fun serializeExecutionContext(executionStack: SerializableExecutionLuaStack?): ByteArray {
        executionStack!!.setJavaLevel(executionStack.getCurrentLevel())
        executionStack.setCurrentLevel(0)
        val byteOutputStream = ByteArrayOutputStream()
        ObjectOutputStream(byteOutputStream).use { oos -> oos.writeObject(luaClosure) }
        return byteOutputStream.toByteArray()
    }
}
