package dr.ramm.bixbyfix

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfo.DEFAULT
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
import android.content.Context
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


class BixbyInterceptService : AccessibilityService() {

    private val DEBUG = false
    private val TAG: String = "BX_FIXUP"
    private val checkLogsPeriod : Long = 250
    private val freshPressTime : Double = 500.0
    private var isNewPress = false
    private var previousPress : Double = 0.0

    private fun debug(TAG2 : String, text : String) {
        if (DEBUG)
            Log.v(TAG2, text)
    }

    private fun getLastPress(): Double {
        var lastPress : Double = 0.0
        try{
            // Get all logs related to Bixby press in epoch
            // I can't pass fckng quotes to exec, so
            // "-t", "\'$previousPress\'",
            val proc = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat", "-s", "PhoneWindowManagerExt", "-e", "startBixbyService", "-d", "-v", "epoch"
                )
            )
            val bufferedReader = BufferedReader(
                InputStreamReader(proc.getInputStream())
            )

            // Get last line
            var line: String? = ""
            var lastLine: String? = ""
            while (bufferedReader.readLine().also({ line = it }) != null) {
                lastLine = line
                debug(TAG, "Curr line is $line")
            }
            debug(TAG, "Last line is $lastLine")
            if (lastLine != null) {
                val epochTime :String? = Regex(pattern = """^.*\d*\.\d*""")
                    .find(input = lastLine)?.value

                if (epochTime != null ) {
                    lastPress = epochTime.toDouble()
                    debug(TAG, "finalResult is $lastPress")
                    return lastPress
                }
            }
        }catch(e: IOException){
            throw Exception(e);
        }catch(e: InterruptedException){
            throw Exception(e);
        }
        return lastPress
    }

    private fun isDisplayEnabled(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private val checkEvent = Thread {
        while (true) {
            val lastPress = getLastPress()
            val time = lastPress - previousPress
            isNewPress =  time > freshPressTime / 1000
            if (isNewPress) {
                previousPress = lastPress
                debug(TAG, "Wew, isNewPress is $isNewPress time is $time")
                if (isDisplayEnabled()) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
                // Behold! Dirty workaround!
                Runtime.getRuntime().exec("logcat -c")
            }

            Thread.sleep(checkLogsPeriod)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        debug(TAG, "onServiceConnected")
        val info = AccessibilityServiceInfo()

        info.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            // Set the type of feedback your service will provide.
            feedbackType = FEEDBACK_ALL_MASK
            flags = DEFAULT;
            notificationTimeout = 100
        }

        this.serviceInfo = info
        checkEvent.start()
    }


    /**
     * Called on an interrupt.
     */
    override fun onInterrupt() {
        debug(TAG, "***** onInterrupt")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        debug(TAG, "onAccessibilityEvent")
    }
}