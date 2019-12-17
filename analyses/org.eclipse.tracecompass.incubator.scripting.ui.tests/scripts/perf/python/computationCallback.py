loadModule("/TraceCompassTest/Test")

class CallbackFunction(object):
    def apply(self, value):
        if value % 2 == 0:
            return value / 2
        return 3 * value + 1

    class Java:
        implements = ['java.util.function.Function']

callbackFunction = CallbackFunction()

doLoopWithCallback(callbackFunction)