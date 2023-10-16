package com.kiliokuara.kuimivm.execute;

import com.kiliokuara.kuimivm.KuimiMethod;
import com.kiliokuara.kuimivm.KuimiObject;

import java.util.BitSet;

public class StackTrace {

    int stackPoint, localFramePoint, frameObjectPointer;

    final KuimiMethod[] stackTrace$trace;
    final int[] stackTrace$localFrameStart;

    final KuimiObject<?>[] frameObjects;
    final int[] localFrameObjectStackStart;

    final BitSet localFrame$hasObjectDeletion;

    public KuimiObject<?> throwable;

    public StackTrace(int stackTraceSize, int maxFrames, int frameObjects) {
        // 0x00_00_00_00

        stackTrace$trace = new KuimiMethod[stackTraceSize];
        stackTrace$localFrameStart = new int[stackTraceSize];
        localFrameObjectStackStart = new int[maxFrames];
        localFrame$hasObjectDeletion = new BitSet(maxFrames);
        this.frameObjects = new KuimiObject[frameObjects];

        stackPoint = -1;
        localFramePoint = -1;
        frameObjectPointer = -1;
    }

    public void enter(KuimiMethod method) {
        stackPoint++;
        stackTrace$trace[stackPoint] = method;
        stackTrace$localFrameStart[stackPoint] = localFramePoint;
    }

    public void pushFrame() {
        localFramePoint++;
        localFrameObjectStackStart[localFramePoint] = frameObjectPointer;
        localFrame$hasObjectDeletion.clear(localFramePoint);
    }

    public int pushObject(KuimiObject<?> object) {
        if (object == null) return 0;
        if (localFramePoint == -1) pushFrame();

        if (localFrame$hasObjectDeletion.get(localFramePoint)) { // has object remove
            var frameObjStart = localFrameObjectStackStart[localFramePoint];
            for (int idx = frameObjStart + 1, ed = frameObjectPointer; idx <= ed; idx++) {
                if (frameObjects[idx] == null) {
                    frameObjects[idx] = object;
                    return ObjectPool.LOCAL_OBJECT_PREFIX | idx;
                }
            }

            // no empty slot found / empty slot was refilled
            localFrame$hasObjectDeletion.clear(localFramePoint);
        }

        frameObjects[frameObjectPointer + 1] = object;
        frameObjectPointer++;

        return ObjectPool.LOCAL_OBJECT_PREFIX | frameObjectPointer;
    }

    public KuimiObject<?> deleteObject(int obj) {
        { // prefix check
            var prefix = obj & ObjectPool.OBJECT_PREFIX;
            if (prefix != ObjectPool.LOCAL_OBJECT_PREFIX) {
                throw new IllegalArgumentException("Object " + obj + "(0x" + Integer.toHexString(obj) + ") is not a local frame object.");
            }
        }

        var idx = obj & ~ObjectPool.OBJECT_PREFIX;
        if (localFramePoint == -1) return null; // ?

        var frameObjStart = localFrameObjectStackStart[localFramePoint];
        if (idx > frameObjStart) {
            var rsp = frameObjects[idx];
            frameObjects[idx] = null;
            localFrame$hasObjectDeletion.set(localFramePoint, true);
            return rsp;
        } else { // out of current frame.
            throw new IllegalStateException("Object " + obj + "(0x" + Integer.toHexString(obj) + ")<" + idx + "> out of current frame, current frame start= " + frameObjStart);
        }
    }

    public KuimiObject<?> resolve(int obj) {
        { // prefix check
            var prefix = obj & ObjectPool.OBJECT_PREFIX;
            if (prefix != ObjectPool.LOCAL_OBJECT_PREFIX) {
                return null;
            }
        }

        var idx = obj & ~ObjectPool.OBJECT_PREFIX;
        if (idx <= frameObjectPointer) return frameObjects[idx];
        return null;
    }

    public void popFrame() {
        // todo: check stack trace
        var fos = localFrameObjectStackStart[localFramePoint];
        for (int i = fos + 1, ed = frameObjectPointer; i < ed; i++) {
            frameObjects[i] = null;
        }
        frameObjectPointer = fos;
        localFramePoint--;
    }

    public void leave() {
        var stackTraceFrameStack = stackTrace$localFrameStart[stackPoint];
        while (localFramePoint > stackTraceFrameStack) {
            popFrame();
        }
        stackTrace$trace[stackPoint] = null;
        stackPoint--;
    }

    public void leave(KuimiMethod method) {
        if (stackTrace$trace[stackPoint] == method) {
            leave();
        } else {
            throw new IllegalStateException();
        }
    }


    public int getStackTracePoint() {
        return stackPoint;
    }

    public int getLocalFramePoint() {
        return localFramePoint;
    }

    public KuimiMethod getStackTraceMethod(int st) {
        return stackTrace$trace[st];
    }
}

