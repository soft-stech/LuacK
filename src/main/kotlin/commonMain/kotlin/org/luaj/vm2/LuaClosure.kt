/*******************************************************************************
 * Copyright (c) 2009 Luaj.org. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.luaj.vm2

import SerializableObjects.SerializableExecutionLuaStack
import SerializableObjects.SerializableLuaClosureStack
import java.lang.System

/**
 * Extension of [LuaFunction] which executes lua bytecode.
 *
 *
 * A [LuaClosure] is a combination of a [Prototype]
 * and a [LuaValue] to use as an environment for execution.
 * Normally the [LuaValue] is a [Globals] in which case the environment
 * will contain standard lua libraries.
 *
 *
 *
 * There are three main ways [LuaClosure] instances are created:
 *
 *  * Construct an instance using [.LuaClosure]
 *  * Construct it indirectly by loading a chunk via [Globals.load]
 *  * Execute the lua bytecode [Lua.OP_CLOSURE] as part of bytecode processing
 *
 *
 *
 * To construct it directly, the [Prototype] is typically created via a compiler such as
 * [org.luaj.vm2.compiler.LuaC]:
 * <pre> `String script = "print( 'hello, world' )";
 * InputStream is = new ByteArrayInputStream(script.getBytes());
 * Prototype p = LuaC.instance.compile(is, "script");
 * LuaValue globals = JsePlatform.standardGlobals();
 * LuaClosure f = new LuaClosure(p, globals);
 * f.call();
`</pre> *
 *
 *
 * To construct it indirectly, the [Globals.load] method may be used:
 * <pre> `Globals globals = JsePlatform.standardGlobals();
 * LuaFunction f = globals.load(new StringReader(script), "script");
 * LuaClosure c = f.checkclosure();  // This may fail if LuaJC is installed.
 * c.call();
`</pre> *
 *
 *
 * In this example, the "checkclosure()" may fail if direct lua-to-java-bytecode
 * compiling using LuaJC is installed, because no LuaClosure is created in that case
 * and the value returned is a [LuaFunction] but not a [LuaClosure].
 *
 *
 * Since a [LuaClosure] is a [LuaFunction] which is a [LuaValue],
 * all the value operations can be used directly such as:
 *
 *  * [LuaValue.call]
 *  * [LuaValue.call]
 *  * [LuaValue.invoke]
 *  * [LuaValue.invoke]
 *  * [LuaValue.method]
 *  * [LuaValue.method]
 *  * [LuaValue.invokemethod]
 *  * [LuaValue.invokemethod]
 *  *  ...
 *
 * @see LuaValue
 *
 * @see LuaFunction
 *
 * @see LuaValue.isclosure
 * @see LuaValue.checkclosure
 * @see LuaValue.optclosure
 * @see LoadState
 *
 * @see Globals.compiler
 */
class LuaClosure
/** Create a closure around a Prototype with a specific environment.
 * If the prototype has upvalues, the environment will be written into the first upvalue.
 * @param p the Prototype to construct this Closure for.
 * @param env the environment to associate with the closure.
 */
    (val p: Prototype, env: LuaValue?) : LuaFunction() {

    var upValues: Array<UpValue?> = when {
        p.upvalues == null || p.upvalues.isEmpty() -> NOUPVALUES
        else -> arrayOfNulls<UpValue>(p.upvalues.size).also { it[0] = UpValue(arrayOf(env), 0) }
    }

     internal val globals: Globals? = if (env is Globals) env else null


    override fun isclosure(): Boolean = true
    override fun optclosure(defval: LuaClosure?): LuaClosure? = this
    override fun checkclosure(): LuaClosure? = this
    override fun getmetatable(): LuaValue? = LuaFunction.s_metatable
    override fun tojstring(): String = "function: $p"

    var executionStack: SerializableExecutionLuaStack? = null

    fun getExecutionContext() : SerializableExecutionLuaStack?{
        return executionStack
    }

    fun setReturnValue(value: String){
        var text : String = ""
        if(value != null) text = value
        executionStack!!.setReturnValue(LuaValue.valueOf(text))
    }

    private fun getNewStack(): Array<LuaValue> {
        val max = p.maxstacksize
        val stack = arrayOfNulls<LuaValue>(max) as Array<LuaValue>
        System.arraycopy(NILS, 0, stack, 0, max)
        val luaClosureStack = SerializableLuaClosureStack()
        luaClosureStack.stack = stack
        luaClosureStack.code = p.code
        luaClosureStack.k = p.k
        executionStack!!.getClosureStacks().push(luaClosureStack)
        return stack
    }

    private fun restoreOrCreateStack(): Array<LuaValue> {
        if (executionStack == null) executionStack = SerializableExecutionLuaStack()
        val stack: Array<LuaValue>
        val level: Int = executionStack?.getCurrentLevel() ?: 0
        val stackAlreadyExists: Boolean = level <= (executionStack!!.getClosureStacks().size ?: 0) - 1
        if (stackAlreadyExists && !executionStack!!.getUserEndCall()) {
            stack = executionStack!!.getClosureStacks().get(executionStack!!.getCurrentLevel()).stack
        } else {
            stack = getNewStack()
        }
        return stack
    }

    override fun call(): LuaValue {
        val stack = restoreOrCreateStack()

        if (executionStack!!.getUserEndCall()) {
            executionStack!!.setCurrentLevel(executionStack!!.getClosureStacks().size - 1)
        }

        for (i in 0 until p.numparams) stack.set(i, LuaValue.NIL)
        return execute(stack, LuaValue.NONE).arg1()
    }

    override suspend fun suspendableCall(): LuaValue {
        val stack = restoreOrCreateStack()

        if (executionStack!!.getUserEndCall()) {
            executionStack!!.setCurrentLevel(executionStack!!.getClosureStacks().size - 1)
        }

        for (i in 0 until p.numparams) stack.set(i, LuaValue.NIL)
        return suspendableExecute(stack, LuaValue.NONE).arg1Suspend()
    }

    override fun call(arg: LuaValue): LuaValue {
        val stack = restoreOrCreateStack()
        when (p.numparams) {
            0 -> return execute(stack, arg).arg1()
            else -> {
                stack[0] = arg
                return execute(stack, LuaValue.NONE).arg1()
            }
        }
    }

    override suspend fun suspendableCall(arg: LuaValue): LuaValue {
        val stack = restoreOrCreateStack()
        when (p.numparams) {
            0 -> return suspendableExecute(stack, arg).arg1Suspend()
            else -> {
                stack[0] = arg
                return suspendableExecute(stack, LuaValue.NONE).arg1Suspend()
            }
        }
    }

    override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue {
        val stack = restoreOrCreateStack()
        when (p.numparams) {
            1 -> {
                stack[0] = arg1
                return execute(stack, arg2).arg1()
            }
            0 -> return execute(stack, if (p.is_vararg != 0) LuaValue.varargsOf(arg1, arg2) else LuaValue.NONE).arg1()
            else -> {
                stack[0] = arg1
                stack[1] = arg2
                return execute(stack, LuaValue.NONE).arg1()
            }
        }
    }

    override suspend fun suspendableCall(arg1: LuaValue, arg2: LuaValue): LuaValue {
        val stack = restoreOrCreateStack()
        when (p.numparams) {
            1 -> {
                stack[0] = arg1
                return suspendableExecute(stack, arg2).arg1Suspend()
            }
            0 -> return suspendableExecute(stack, if (p.is_vararg != 0) LuaValue.varargsOf(arg1, arg2) else LuaValue.NONE).arg1Suspend()
            else -> {
                stack[0] = arg1
                stack[1] = arg2
                return suspendableExecute(stack, LuaValue.NONE).arg1Suspend()
            }
        }
    }

    override fun call(arg1: LuaValue, arg2: LuaValue, arg3: LuaValue): LuaValue {
        val stack = restoreOrCreateStack()
        return when (p.numparams) {
            0 -> execute(stack, if (p.is_vararg != 0) LuaValue.varargsOf(arg1, arg2, arg3) else LuaValue.NONE).arg1()
            1 -> {
                stack[0] = arg1
                execute(stack, if (p.is_vararg != 0) LuaValue.varargsOf(arg2, arg3) else LuaValue.NONE).arg1()
            }
            2 -> {
                stack[0] = arg1
                stack[1] = arg2
                execute(stack, arg3).arg1()
            }
            else -> {
                stack[0] = arg1
                stack[1] = arg2
                stack[2] = arg3
                execute(stack, LuaValue.NONE).arg1()
            }
        }
    }

    override suspend fun suspendableCall(arg1: LuaValue, arg2: LuaValue, arg3: LuaValue): LuaValue {
        val stack = restoreOrCreateStack()
        return when (p.numparams) {
            0 -> suspendableExecute(stack, if (p.is_vararg != 0) LuaValue.varargsOf(arg1, arg2, arg3) else LuaValue.NONE).arg1Suspend()
            1 -> {
                stack[0] = arg1
                suspendableExecute(stack, if (p.is_vararg != 0) LuaValue.varargsOf(arg2, arg3) else LuaValue.NONE).arg1Suspend()
            }
            2 -> {
                stack[0] = arg1
                stack[1] = arg2
                suspendableExecute(stack, arg3).arg1Suspend()
            }
            else -> {
                stack[0] = arg1
                stack[1] = arg2
                stack[2] = arg3
                suspendableExecute(stack, LuaValue.NONE).arg1Suspend()
            }
        }
    }

    override fun invoke(varargs: Varargs): Varargs = onInvoke(varargs).eval()

    override fun onInvoke(varargs: Varargs): Varargs {
        val stack = restoreOrCreateStack()
        for (i in 0 until p.numparams) stack[i] = varargs.arg(i + 1)
        return execute(stack, if (p.is_vararg != 0) varargs.subargs(p.numparams + 1) else LuaValue.NONE)
    }

    override suspend fun invokeSuspend(varargs: Varargs): Varargs = onInvokeSuspend(
        varargs).eval()

    override suspend fun onInvokeSuspend(varargs: Varargs): Varargs {
        val stack = restoreOrCreateStack()
        for (i in 0 until p.numparams) stack[i] = varargs.arg(i + 1)
        return suspendableExecute(stack, if (p.is_vararg != 0) varargs.subargs(p.numparams + 1) else LuaValue.NONE)
    }

    fun stop(){
        executionStack!!.setEndOfScript(false)
        for (item in executionStack!!.getClosureStacks())
           item.pc = item.code.size-2
    }

    protected suspend fun suspendableExecute(stack: Array<LuaValue>, varargs: Varargs): Varargs {
        val field: SerializableLuaClosureStack = executionStack!!.getClosureStacks().get(executionStack!!.getCurrentLevel())

        // upvalues are only possible when closures create closures
        // TODO: use linked list.
        val openups = if (p.p.size > 0) arrayOfNulls<UpValue>(stack.size) else null

        // allow for debug hooks
        if (globals != null && globals.debuglib != null)
            globals.debuglib!!.onCall(this, varargs, stack)

        // process instructions
        try {
            loop@while (true) {
                if (globals != null && globals.debuglib != null)
                    globals.debuglib!!.onInstruction(field.pc, field.v, field.top)

                // pull out instruction
                field.i = field.code[field.pc]
                field.a = field.i shr 6 and 0xff

                // process the op code
                when (field.i and 0x3f) {

                    Lua.OP_MOVE/*	A B	R(A):= R(B)					*/ -> {
                        stack[field.a] = stack[field.i.ushr(23)]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LOADK/*	A Bx	R(A):= Kst(Bx)					*/ -> {
                        stack[field.a] = field.k[field.i.ushr(14)]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LOADBOOL/*	A B C	R(A):= (Bool)B: if (C) pc++			*/ -> {
                        stack[field.a] = if (field.i.ushr(23) != 0) LuaValue.TRUE else LuaValue.FALSE
                        if (field.i and (0x1ff shl 14) != 0)
                            ++field.pc /* skip next instruction (if C) */
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LOADNIL /*	A B	R(A):= ...:= R(A+B):= nil			*/ -> {
                        field.b = field.i.ushr(23)
                        while (field.b-- >= 0)
                            stack[field.a++] = LuaValue.NIL
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_GETUPVAL /*	A B	R(A):= UpValue[B]				*/ -> {
                        stack[field.a] = upValues[field.i.ushr(23)]!!.value!!
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_GETTABUP /*	A B C	R(A) := UpValue[B][RK(C)]			*/ -> {
                        stack[field.a] = upValues[field.i.ushr(23)]!!.value!![if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff
                        ) field.k[field.c and 0x0ff] else stack[field.c]]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_GETTABLE /*	A B C	R(A):= R(B)[RK(C)]				*/ -> {
                        stack[field.a] = stack[field.i.ushr(23)][if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SETTABUP /*	A B C	UpValue[A][RK(B)] := RK(C)			*/ -> {
                        upValues[field.a]!!.value!![if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]] =
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SETUPVAL /*	A B	UpValue[B]:= R(A)				*/ -> {
                        upValues[field.i.ushr(23)]?.value = stack[field.a]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SETTABLE /*	A B C	R(A)[RK(B)]:= RK(C)				*/ -> {
                        stack[field.a][if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]] =
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_NEWTABLE /*	A B C	R(A):= {} (size = B,C)				*/ -> {
                        stack[field.a] = LuaTable(field.i.ushr(23), field.i shr 14 and 0x1ff)
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SELF /*	A B C	R(A+1):= R(B): R(A):= R(B)[RK(C)]		*/ -> {
                        stack[field.a + 1] = (run { field.o = stack[field.i.ushr(23)]; field.o }!!)
                        stack[field.a] = field.o!![if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_ADD /*	A B C	R(A):= RK(B) + RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).add(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SUB /*	A B C	R(A):= RK(B) - RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).sub(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_MUL /*	A B C	R(A):= RK(B) * RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).mul(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_DIV /*	A B C	R(A):= RK(B) / RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).div(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_MOD /*	A B C	R(A):= RK(B) % RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).mod(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_POW /*	A B C	R(A):= RK(B) ^ RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).pow(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_UNM /*	A B	R(A):= -R(B)					*/ -> {
                        stack[field.a] = stack[field.i.ushr(23)].neg()
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_NOT /*	A B	R(A):= not R(B)				*/ -> {
                        stack[field.a] = stack[field.i.ushr(23)].not()
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LEN /*	A B	R(A):= length of R(B)				*/ -> {
                        stack[field.a] = stack[field.i.ushr(23)].len()
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_CONCAT /*	A B C	R(A):= R(B).. ... ..R(C)			*/ -> {
                        field.b = field.i.ushr(23)
                        field.c = field.i shr 14 and 0x1ff
                        run {
                            if (field.c > field.b + 1) {
                                var sb = stack[field.c].buffer()
                                while (--field.c >= field.b)
                                    sb = stack[field.c].concatSuspend(sb)
                                stack[field.a] = sb.value()
                            } else {
                                stack[field.a] = stack[field.c - 1].concatSuspend(stack[field.c])
                            }
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_JMP /*	sBx	pc+=sBx					*/ -> {
                        field.pc += field.i.ushr(14) - 0x1ffff
                        if (field.a > 0) {
                            --field.a
                            field.b = openups!!.size
                            while (--field.b >= 0)
                                if (openups[field.b] != null && openups[field.b]!!.index >= field.a) {
                                    openups[field.b]!!.close()
                                    openups[field.b] = null
                                }
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_EQ /*	A B C	if ((RK(B) == RK(C)) ~= A) then pc++		*/ -> {
                        if ((if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).eq_b(
                                if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                            ) != (field.a != 0)
                        )
                            ++field.pc
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LT /*	A B C	if ((RK(B) <  RK(C)) ~= A) then pc++  		*/ -> {
                        if ((if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).lt_b(
                                if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                            ) != (field.a != 0)
                        )
                            ++field.pc
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LE /*	A B C	if ((RK(B) <= RK(C)) ~= A) then pc++  		*/ -> {
                        if ((if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).lteq_b(
                                if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                            ) != (field.a != 0)
                        )
                            ++field.pc
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_TEST /*	A C	if not (R(A) <=> C) then pc++			*/ -> {
                        if (stack[field.a].toboolean() != (field.i and (0x1ff shl 14) != 0))
                            ++field.pc
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_TESTSET /*	A B C	if (R(B) <=> C) then R(A):= R(B) else pc++	*/ -> {
                        /* note: doc appears to be reversed */
                        if ((run { field.o = stack[field.i.ushr(23)]; field.o })!!.toboolean() != (field.i and (0x1ff shl 14) != 0))
                            ++field.pc
                        else
                            stack[field.a] = field.o!! // TODO: should be sBx?
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_CALL /*	A B C	R(A), ... ,R(A+C-2):= R(A)(R(A+1), ... ,R(A+B-1)) */ -> {
                            executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() + 1)
                            when (field.i and (Lua.MASK_B or Lua.MASK_C)) {
                            1 shl Lua.POS_B or (0 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    field.v = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else{
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    field.v = stack[field.a].invokeSuspend(LuaValue.NONE)
                                }

                                field.top = field.a + field.v.narg()
                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            2 shl Lua.POS_B or (0 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    field.v = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else {
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    field.v = stack[field.a].invokeSuspend(stack[field.a + 1])
                                }

                                field.top = field.a + field.v.narg()
                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            1 shl Lua.POS_B or (1 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else{
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a].suspendableCall()
                                }


                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            2 shl Lua.POS_B or (1 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else{
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a].suspendableCall(stack[field.a + 1])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            3 shl Lua.POS_B or (1 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else{
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a].suspendableCall(stack[field.a + 1], stack[field.a + 2])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            4 shl Lua.POS_B or (1 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else {
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a].suspendableCall(stack[field.a + 1], stack[field.a + 2], stack[field.a + 3])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            1 shl Lua.POS_B or (2 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else {
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a] = stack[field.a].suspendableCall()
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            2 shl Lua.POS_B or (2 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    stack[field.a] = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else {
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a] = stack[field.a].suspendableCall(stack[field.a + 1])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            3 shl Lua.POS_B or (2 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    stack[field.a] = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else {
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a] = stack[field.a].suspendableCall(stack[field.a + 1], stack[field.a + 2])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            4 shl Lua.POS_B or (2 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    stack[field.a] = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else {
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a] = stack[field.a].suspendableCall(stack[field.a + 1], stack[field.a + 2], stack[field.a + 3])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            else -> {
                                field.b = field.i.ushr(23)
                                field.c = field.i shr 14 and 0x1ff
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    field.v = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else{
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack

                                    var c = if (field.b > 0)
                                        LuaValue.varargsOf(stack, field.a + 1, field.b - 1)
                                    else
                                        LuaValue.varargsOf(stack, field.a + 1, field.top - field.v.narg() - (field.a + 1), field.v)

                                    if(stack[field.a].isnil())
                                        field.v = stack[field.a].invoke(c)  // from prev top
                                    else
                                        field.v = stack[field.a].invokeSuspend(c)
                                }
                                if (field.c > 0) {
                                    field.v.copyto(stack, field.a, field.c - 1)
                                    field.v = LuaValue.NONE
                                } else {
                                    field.top = field.a + field.v.narg()
                                    field.v = field.v.dealias()
                                }
                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                        }

                    }

                    Lua.OP_TAILCALL /*	A B C	return R(A)(R(A+1), ... ,R(A+B-1))		*/ -> when (field.i and Lua.MASK_B) {
                        1 shl Lua.POS_B -> return TailcallVarargs(stack[field.a], LuaValue.NONE)
                        2 shl Lua.POS_B -> return TailcallVarargs(stack[field.a], stack[field.a + 1])
                        3 shl Lua.POS_B -> return TailcallVarargs(
                            stack[field.a],
                            LuaValue.varargsOf(stack[field.a + 1], stack[field.a + 2])
                        )
                        4 shl Lua.POS_B -> return TailcallVarargs(
                            stack[field.a],
                            LuaValue.varargsOf(stack[field.a + 1], stack[field.a + 2], field.stack[field.a + 3])
                        )
                        else -> {
                            field.b = field.i.ushr(23)
                            field.v = if (field.b > 0)
                                LuaValue.varargsOf(stack, field.a + 1, field.b - 1)
                            else
                            // exact arg count
                                LuaValue.varargsOf(stack, field.a + 1, field.top - field.v.narg() - (field.a + 1), field.v) // from prev top
                            return TailcallVarargs(stack[field.a], field.v)
                        }
                    }

                    Lua.OP_RETURN /*	A B	return R(A), ... ,R(A+B-2)	(see note)	*/ -> {
                        field.b = field.i.ushr(23)
                        when (field.b) {
                            0 -> return LuaValue.varargsOf(stack, field.a, field.top - field.v.narg() - field.a, field.v)
                            1 -> return LuaValue.NONE
                            2 -> return stack[field.a]
                            else -> return LuaValue.varargsOf(stack, field.a, field.b - 1)
                        }
                    }

                    Lua.OP_FORLOOP /*	A sBx	R(A)+=R(A+2): if R(A) <?= R(A+1) then { pc+=sBx: R(A+3)=R(A) }*/ -> {
                        run {
                            val limit = stack[field.a + 1]
                            val step = stack[field.a + 2]
                            val idx = step.add(stack[field.a])
                            if (if (step.gt_b(0)) idx.lteq_b(limit) else idx.gteq_b(limit)) {
                                stack[field.a] = idx
                                stack[field.a + 3] = idx
                                field.pc += field.i.ushr(14) - 0x1ffff
                            }
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_FORPREP /*	A sBx	R(A)-=R(A+2): pc+=sBx				*/ -> {
                        run {
                            val init = stack[field.a].checknumber("'for' initial value must be a number")
                            val limit = stack[field.a + 1].checknumber("'for' limit must be a number")
                            val step = stack[field.a + 2].checknumber("'for' step must be a number")
                            stack[field.a] = init.sub(step)
                            stack[field.a + 1] = limit
                            stack[field.a + 2] = step
                            field.pc += field.i.ushr(14) - 0x1ffff
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_TFORCALL /* A C	R(A+3), ... ,R(A+2+C) := R(A)(R(A+1), R(A+2));	*/ -> {
                        if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                        field.v = stack[field.a].invokeSuspend(LuaValue.varargsOf(stack[field.a + 1], stack[field.a + 2]))
                        field.c = field.i shr 14 and 0x1ff
                        while (--field.c >= 0)
                            stack[field.a + 3 + field.c] = field.v.arg(field.c + 1)
                        field.v = LuaValue.NONE
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_TFORLOOP /* A sBx	if R(A+1) ~= nil then { R(A)=R(A+1); pc += sBx */ -> {
                        if (!stack[field.a + 1].isnil()) { /* continue loop? */
                            stack[field.a] = stack[field.a + 1]  /* save control varible. */
                            field.pc += field.i.ushr(14) - 0x1ffff
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SETLIST /*	A B C	R(A)[(C-1)*FPF+i]:= R(A+i), 1 <= i <= B	*/ -> {
                        run {
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) == 0)
                                field.c = field.code[++field.pc]
                            val offset = (field.c - 1) * Lua.LFIELDS_PER_FLUSH
                            field.o = stack[field.a]
                            if ((run { field.b = field.i.ushr(23); field.b }) == 0) {
                                field.b = field.top - field.a - 1
                                val m = field.b - field.v.narg()
                                var j = 1
                                while (j <= m) {
                                    field.o!![offset + j] = stack[field.a + j]
                                    j++
                                }
                                while (j <= field.b) {
                                    field.o!![offset + j] = field.v.arg(j - m)
                                    j++
                                }
                            } else {
                                field.o!!.presize(offset + field.b)
                                for (j in 1..field.b)
                                    field.o!![offset + j] = stack[field.a + j]
                            }
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_CLOSURE /*	A Bx	R(A):= closure(KPROTO[Bx])	*/ -> {
                        run {
                            val newp = p.p[field.i.ushr(14)]
                            val ncl = LuaClosure(newp, globals)
                            val uv = newp.upvalues
                            var j = 0
                            val nup = uv.size
                            while (j < nup) {
                                if (uv[j].instack)
                                /* upvalue refes to local variable? */
                                    ncl.upValues[j] = findupval(stack as Array<LuaValue?>, uv[j].idx, openups!!)
                                else
                                /* get upvalue from enclosing function */
                                    ncl.upValues[j] = upValues[uv[j].idx.toInt()]
                                ++j
                            }
                            stack[field.a] = ncl
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_VARARG /*	A B	R(A), R(A+1), ..., R(A+B-1) = vararg		*/ -> {
                        field.b = field.i.ushr(23)
                        if (field.b == 0) {
                            field.top = field.a + (run { field.b = varargs.narg(); field.b })
                            field.v = varargs
                        } else {
                            for (j in 1 until field.b)
                                stack[field.a + j - 1] = varargs.arg(j)
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_EXTRAARG -> throw IllegalArgumentException("Uexecutable opcode: OP_EXTRAARG")

                    else -> throw IllegalArgumentException("Illegal opcode: " + (field.i and 0x3f))
                }
                ++field.pc
            }
        } catch (le: LuaError) {
            if (le.traceback == null) processErrorHooks(le, p, field.pc)
            throw le
        } catch (e: Exception) {
            val le = LuaError(e)
            processErrorHooks(le, p, field.pc)
            throw le
        }finally {
            executionStack!!.getClosureStacks().pop()
            if (openups != null) {
                var u = openups.size
                while (--u >= 0) if (openups[u] != null) openups[u]!!.close()
            }
            if (globals != null && globals.debuglib != null) globals.debuglib!!.onReturn()
        }
    }

    protected fun execute(stack: Array<LuaValue>, varargs: Varargs): Varargs {
        val field: SerializableLuaClosureStack = executionStack!!.getClosureStacks().get(executionStack!!.getCurrentLevel())

        // upvalues are only possible when closures create closures
        // TODO: use linked list.
        val openups = if (p.p.size > 0) arrayOfNulls<UpValue>(stack.size) else null

        // allow for debug hooks
        if (globals != null && globals.debuglib != null)
            globals.debuglib!!.onCall(this, varargs, stack)

        // process instructions
        try {
            loop@while (true) {
                if (globals != null && globals.debuglib != null)
                    globals.debuglib!!.onInstruction(field.pc, field.v, field.top)

                // pull out instruction
                field.i = field.code[field.pc]
                field.a = field.i shr 6 and 0xff

                // process the op code
                when (field.i and 0x3f) {

                    Lua.OP_MOVE/*	A B	R(A):= R(B)					*/ -> {
                        stack[field.a] = stack[field.i.ushr(23)]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LOADK/*	A Bx	R(A):= Kst(Bx)					*/ -> {
                        stack[field.a] = field.k[field.i.ushr(14)]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LOADBOOL/*	A B C	R(A):= (Bool)B: if (C) pc++			*/ -> {
                        stack[field.a] = if (field.i.ushr(23) != 0) LuaValue.TRUE else LuaValue.FALSE
                        if (field.i and (0x1ff shl 14) != 0)
                            ++field.pc /* skip next instruction (if C) */
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LOADNIL /*	A B	R(A):= ...:= R(A+B):= nil			*/ -> {
                        field.b = field.i.ushr(23)
                        while (field.b-- >= 0)
                            stack[field.a++] = LuaValue.NIL
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_GETUPVAL /*	A B	R(A):= UpValue[B]				*/ -> {
                        stack[field.a] = upValues[field.i.ushr(23)]!!.value!!
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_GETTABUP /*	A B C	R(A) := UpValue[B][RK(C)]			*/ -> {
                        stack[field.a] = upValues[field.i.ushr(23)]!!.value!![if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff
                        ) field.k[field.c and 0x0ff] else stack[field.c]]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_GETTABLE /*	A B C	R(A):= R(B)[RK(C)]				*/ -> {
                        stack[field.a] = stack[field.i.ushr(23)][if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SETTABUP /*	A B C	UpValue[A][RK(B)] := RK(C)			*/ -> {
                        upValues[field.a]!!.value!![if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]] =
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SETUPVAL /*	A B	UpValue[B]:= R(A)				*/ -> {
                        upValues[field.i.ushr(23)]?.value = stack[field.a]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SETTABLE /*	A B C	R(A)[RK(B)]:= RK(C)				*/ -> {
                        stack[field.a][if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]] =
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_NEWTABLE /*	A B C	R(A):= {} (size = B,C)				*/ -> {
                        stack[field.a] = LuaTable(field.i.ushr(23), field.i shr 14 and 0x1ff)
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SELF /*	A B C	R(A+1):= R(B): R(A):= R(B)[RK(C)]		*/ -> {
                        stack[field.a + 1] = (run { field.o = stack[field.i.ushr(23)]; field.o }!!)
                        stack[field.a] = field.o!![if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]]
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_ADD /*	A B C	R(A):= RK(B) + RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).add(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SUB /*	A B C	R(A):= RK(B) - RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).sub(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_MUL /*	A B C	R(A):= RK(B) * RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).mul(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_DIV /*	A B C	R(A):= RK(B) / RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).div(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_MOD /*	A B C	R(A):= RK(B) % RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).mod(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_POW /*	A B C	R(A):= RK(B) ^ RK(C)				*/ -> {
                        stack[field.a] = (if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).pow(
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                        )
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_UNM /*	A B	R(A):= -R(B)					*/ -> {
                        stack[field.a] = stack[field.i.ushr(23)].neg()
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_NOT /*	A B	R(A):= not R(B)				*/ -> {
                        stack[field.a] = stack[field.i.ushr(23)].not()
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LEN /*	A B	R(A):= length of R(B)				*/ -> {
                        stack[field.a] = stack[field.i.ushr(23)].len()
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_CONCAT /*	A B C	R(A):= R(B).. ... ..R(C)			*/ -> {
                        field.b = field.i.ushr(23)
                        field.c = field.i shr 14 and 0x1ff
                        run {
                            if (field.c > field.b + 1) {
                                var sb = stack[field.c].buffer()
                                while (--field.c >= field.b)
                                    sb = stack[field.c].concat(sb)
                                stack[field.a] = sb.value()
                            } else {
                                stack[field.a] = stack[field.c - 1].concat(stack[field.c])
                            }
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_JMP /*	sBx	pc+=sBx					*/ -> {
                        field.pc += field.i.ushr(14) - 0x1ffff
                        if (field.a > 0) {
                            --field.a
                            field.b = openups!!.size
                            while (--field.b >= 0)
                                if (openups[field.b] != null && openups[field.b]!!.index >= field.a) {
                                    openups[field.b]!!.close()
                                    openups[field.b] = null
                                }
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_EQ /*	A B C	if ((RK(B) == RK(C)) ~= A) then pc++		*/ -> {
                        if ((if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).eq_b(
                                if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                            ) != (field.a != 0)
                        )
                            ++field.pc
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LT /*	A B C	if ((RK(B) <  RK(C)) ~= A) then pc++  		*/ -> {
                        if ((if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).lt_b(
                                if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                            ) != (field.a != 0)
                        )
                            ++field.pc
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_LE /*	A B C	if ((RK(B) <= RK(C)) ~= A) then pc++  		*/ -> {
                        if ((if ((run { field.b = field.i.ushr(23); field.b }) > 0xff) field.k[field.b and 0x0ff] else stack[field.b]).lteq_b(
                                if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) > 0xff) field.k[field.c and 0x0ff] else stack[field.c]
                            ) != (field.a != 0)
                        )
                            ++field.pc
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_TEST /*	A C	if not (R(A) <=> C) then pc++			*/ -> {
                        if (stack[field.a].toboolean() != (field.i and (0x1ff shl 14) != 0))
                            ++field.pc
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_TESTSET /*	A B C	if (R(B) <=> C) then R(A):= R(B) else pc++	*/ -> {
                        /* note: doc appears to be reversed */
                        if ((run { field.o = stack[field.i.ushr(23)]; field.o })!!.toboolean() != (field.i and (0x1ff shl 14) != 0))
                            ++field.pc
                        else
                            stack[field.a] = field.o!! // TODO: should be sBx?
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_CALL /*	A B C	R(A), ... ,R(A+C-2):= R(A)(R(A+1), ... ,R(A+B-1)) */ -> {
                        executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() + 1)
                        when (field.i and (Lua.MASK_B or Lua.MASK_C)) {
                            1 shl Lua.POS_B or (0 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    field.v = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else field.v = stack[field.a].invoke(LuaValue.NONE)

                                field.top = field.a + field.v.narg()
                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            2 shl Lua.POS_B or (0 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    field.v = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else
                                    field.v = stack[field.a].invoke(stack[field.a + 1])

                                field.top = field.a + field.v.narg()
                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            1 shl Lua.POS_B or (1 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else{
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a].call()
                                }


                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            2 shl Lua.POS_B or (1 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else{
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a].call(stack[field.a + 1])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            3 shl Lua.POS_B or (1 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else{
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a].call(stack[field.a + 1], stack[field.a + 2])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            4 shl Lua.POS_B or (1 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else {
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a].call(stack[field.a + 1], stack[field.a + 2], stack[field.a + 3])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            1 shl Lua.POS_B or (2 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else {
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a] = stack[field.a].call()
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            2 shl Lua.POS_B or (2 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    stack[field.a] = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else {
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a] = stack[field.a].call(stack[field.a + 1])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            3 shl Lua.POS_B or (2 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    stack[field.a] = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else {
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a] = stack[field.a].call(stack[field.a + 1], stack[field.a + 2])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            4 shl Lua.POS_B or (2 shl Lua.POS_C) -> {
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    stack[field.a] = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else {
                                    if(stack[field.a] is LuaClosure) (stack[field.a] as LuaClosure).executionStack = executionStack
                                    stack[field.a] = stack[field.a].call(stack[field.a + 1], stack[field.a + 2], stack[field.a + 3])
                                }

                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                            else -> {
                                field.b = field.i.ushr(23)
                                field.c = field.i shr 14 and 0x1ff
                                if (executionStack!!.getCurrentLevel() === executionStack!!.getJavaLevel()) {
                                    field.v = executionStack!!.getReturnValue()
                                    executionStack!!.setJavaLevel(Int.MAX_VALUE)
                                } else
                                    field.v = stack[field.a].invoke(
                                        if (field.b > 0)
                                            LuaValue.varargsOf(stack, field.a + 1, field.b - 1)
                                        else
                                        // exact arg count
                                            LuaValue.varargsOf(stack, field.a + 1, field.top - field.v.narg() - (field.a + 1), field.v)
                                    )  // from prev top
                                if (field.c > 0) {
                                    field.v.copyto(stack, field.a, field.c - 1)
                                    field.v = LuaValue.NONE
                                } else {
                                    field.top = field.a + field.v.narg()
                                    field.v = field.v.dealias()
                                }
                                executionStack!!.setCurrentLevel(executionStack!!.getCurrentLevel() - 1)
                                ++field.pc
                                continue@loop
                            }
                        }

                    }

                    Lua.OP_TAILCALL /*	A B C	return R(A)(R(A+1), ... ,R(A+B-1))		*/ -> when (field.i and Lua.MASK_B) {
                        1 shl Lua.POS_B -> return TailcallVarargs(stack[field.a], LuaValue.NONE)
                        2 shl Lua.POS_B -> return TailcallVarargs(stack[field.a], stack[field.a + 1])
                        3 shl Lua.POS_B -> return TailcallVarargs(
                            stack[field.a],
                            LuaValue.varargsOf(stack[field.a + 1], stack[field.a + 2])
                        )
                        4 shl Lua.POS_B -> return TailcallVarargs(
                            stack[field.a],
                            LuaValue.varargsOf(stack[field.a + 1], stack[field.a + 2], field.stack[field.a + 3])
                        )
                        else -> {
                            field.b = field.i.ushr(23)
                            field.v = if (field.b > 0)
                                LuaValue.varargsOf(stack, field.a + 1, field.b - 1)
                            else
                            // exact arg count
                                LuaValue.varargsOf(stack, field.a + 1, field.top - field.v.narg() - (field.a + 1), field.v) // from prev top
                            return TailcallVarargs(stack[field.a], field.v)
                        }
                    }

                    Lua.OP_RETURN /*	A B	return R(A), ... ,R(A+B-2)	(see note)	*/ -> {
                        field.b = field.i.ushr(23)
                        when (field.b) {
                            0 -> return LuaValue.varargsOf(stack, field.a, field.top - field.v.narg() - field.a, field.v)
                            1 -> return LuaValue.NONE
                            2 -> return stack[field.a]
                            else -> return LuaValue.varargsOf(stack, field.a, field.b - 1)
                        }
                    }

                    Lua.OP_FORLOOP /*	A sBx	R(A)+=R(A+2): if R(A) <?= R(A+1) then { pc+=sBx: R(A+3)=R(A) }*/ -> {
                        run {
                            val limit = stack[field.a + 1]
                            val step = stack[field.a + 2]
                            val idx = step.add(stack[field.a])
                            if (if (step.gt_b(0)) idx.lteq_b(limit) else idx.gteq_b(limit)) {
                                stack[field.a] = idx
                                stack[field.a + 3] = idx
                                field.pc += field.i.ushr(14) - 0x1ffff
                            }
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_FORPREP /*	A sBx	R(A)-=R(A+2): pc+=sBx				*/ -> {
                        run {
                            val init = stack[field.a].checknumber("'for' initial value must be a number")
                            val limit = stack[field.a + 1].checknumber("'for' limit must be a number")
                            val step = stack[field.a + 2].checknumber("'for' step must be a number")
                            stack[field.a] = init.sub(step)
                            stack[field.a + 1] = limit
                            stack[field.a + 2] = step
                            field.pc += field.i.ushr(14) - 0x1ffff
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_TFORCALL /* A C	R(A+3), ... ,R(A+2+C) := R(A)(R(A+1), R(A+2));	*/ -> {
                        field.v = stack[field.a].invoke(LuaValue.varargsOf(stack[field.a + 1], stack[field.a + 2]))
                        field.c = field.i shr 14 and 0x1ff
                        while (--field.c >= 0)
                            stack[field.a + 3 + field.c] = field.v.arg(field.c + 1)
                        field.v = LuaValue.NONE
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_TFORLOOP /* A sBx	if R(A+1) ~= nil then { R(A)=R(A+1); pc += sBx */ -> {
                        if (!stack[field.a + 1].isnil()) { /* continue loop? */
                            stack[field.a] = stack[field.a + 1]  /* save control varible. */
                            field.pc += field.i.ushr(14) - 0x1ffff
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_SETLIST /*	A B C	R(A)[(C-1)*FPF+i]:= R(A+i), 1 <= i <= B	*/ -> {
                        run {
                            if ((run { field.c = field.i shr 14 and 0x1ff; field.c }) == 0)
                                field.c = field.code[++field.pc]
                            val offset = (field.c - 1) * Lua.LFIELDS_PER_FLUSH
                            field.o = stack[field.a]
                            if ((run { field.b = field.i.ushr(23); field.b }) == 0) {
                                field.b = field.top - field.a - 1
                                val m = field.b - field.v.narg()
                                var j = 1
                                while (j <= m) {
                                    field.o!![offset + j] = stack[field.a + j]
                                    j++
                                }
                                while (j <= field.b) {
                                    field.o!![offset + j] = field.v.arg(j - m)
                                    j++
                                }
                            } else {
                                field.o!!.presize(offset + field.b)
                                for (j in 1..field.b)
                                    field.o!![offset + j] = stack[field.a + j]
                            }
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_CLOSURE /*	A Bx	R(A):= closure(KPROTO[Bx])	*/ -> {
                        run {
                            val newp = p.p[field.i.ushr(14)]
                            val ncl = LuaClosure(newp, globals)
                            val uv = newp.upvalues
                            var j = 0
                            val nup = uv.size
                            while (j < nup) {
                                if (uv[j].instack)
                                /* upvalue refes to local variable? */
                                    ncl.upValues[j] = findupval(stack as Array<LuaValue?>, uv[j].idx, openups!!)
                                else
                                /* get upvalue from enclosing function */
                                    ncl.upValues[j] = upValues[uv[j].idx.toInt()]
                                ++j
                            }
                            stack[field.a] = ncl
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_VARARG /*	A B	R(A), R(A+1), ..., R(A+B-1) = vararg		*/ -> {
                        field.b = field.i.ushr(23)
                        if (field.b == 0) {
                            field.top = field.a + (run { field.b = varargs.narg(); field.b })
                            field.v = varargs
                        } else {
                            for (j in 1 until field.b)
                                stack[field.a + j - 1] = varargs.arg(j)
                        }
                        ++field.pc
                        continue@loop
                    }

                    Lua.OP_EXTRAARG -> throw IllegalArgumentException("Uexecutable opcode: OP_EXTRAARG")

                    else -> throw IllegalArgumentException("Illegal opcode: " + (field.i and 0x3f))
                }
                ++field.pc
            }
        } catch (le: LuaError) {
            if (le.traceback == null) processErrorHooks(le, p, field.pc)
            throw le
        } catch (e: Exception) {
            val le = LuaError(e)
            processErrorHooks(le, p, field.pc)
            throw le
        }finally {
            executionStack!!.getClosureStacks().pop()
            if (openups != null) {
                var u = openups.size
                while (--u >= 0) if (openups[u] != null) openups[u]!!.close()
            }
            if (globals != null && globals.debuglib != null) globals.debuglib!!.onReturn()
        }
    }

    /**
     * Run the error hook if there is one
     * @param msg the message to use in error hook processing.
     */
    internal fun errorHook(msg: String, level: Int): String {
        if (globals == null) return msg
        val r = globals.running
        if (r.errorfunc == null) return if (globals.debuglib != null) msg + "\n" + globals.debuglib!!.traceback(level) else msg
        val e = r.errorfunc
        r.errorfunc = null
        return try {
            e!!.call(LuaValue.valueOf(msg)).tojstring()
        } catch (t: Throwable) {
            "error in error handling"
        } finally {
            r.errorfunc = e
        }
    }

    private fun processErrorHooks(le: LuaError, p: Prototype, pc: Int) {
        le.fileline = ((if (p.source != null) p.source.tojstring() else "?") + ":" + if (p.lineinfo != null && pc >= 0 && pc < p.lineinfo.size) p.lineinfo[pc].toString() else "?")
        le.traceback = errorHook(le.message!!, le.level)
    }

    private fun findupval(stack: Array<LuaValue?>, idx: Short, openups: Array<UpValue?>): UpValue? {
        val n = openups.size
        for (i in 0 until n) if (openups[i] != null && openups[i]!!.index == idx.toInt()) return openups[i]
        for (i in 0 until n) if (openups[i] == null) return UpValue(stack, idx.toInt()).also { openups[i] = it }
        LuaValue.error("No space for upvalue")
        return null
    }

    protected fun getUpvalue(i: Int): LuaValue? = upValues[i]?.value
    protected fun setUpvalue(i: Int, v: LuaValue) = run { upValues[i]?.value = v }
    override fun name(): String = "<" + p.shortsource() + ":" + p.linedefined + ">"

    companion object {
        private val NOUPVALUES = arrayOfNulls<UpValue>(0)
    }
}
