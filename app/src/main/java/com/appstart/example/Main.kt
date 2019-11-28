package com.appstart.example

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.TimeUnit
import kotlin.time.*

@ExperimentalTime
class MyApplication: Application() {

    /// This is the first line of our code that will be executed
    /// (Step 1) This will include dex loading time
    /// This need to be the FIRST line of the class
    /// The system will init all the variable one by one in the class from top down
    val dexLoadTime = SystemClock.currentThreadTimeMillis().toDouble().toDuration(TimeUnit.MILLISECONDS)

    /// (Step 2) Start a clock now to measure variable definition on the class
    private val singleTracer = MonoClock.markNow()

    ///////////////////////////////////////////////
    ///                                         ///
    ///  Create all your class attribute here   ///
    ///    (simulating variable definition)     ///
    private val dummy = SystemClock.sleep(50)
    ///////////////////////////////////////////////

    var contentProviderInit: Duration? = null
    var applicationOnCreate: Duration? = null

    lateinit var activityCreateDelta: ClockMark

    override fun onCreate() = MonoClock.markNow().also {
        /// (Step 3) At this point the content providers are already init
        contentProviderInit = contentProviderTracker.elapsedNow()

        super.onCreate()
        /////////////////////////////////////////////
        ///                                       ///
        /// Init all your component here as usual ///
        ///      (simulating component init)      ///
        SystemClock.sleep(100)
        /////////////////////////////////////////////

    }.let {
        applicationOnCreate = it.elapsedNow()
        activityCreateDelta = MonoClock.markNow()
    }

    /// This need to be the LAST line of code in the class
    val applicationClassAttributesInit = singleTracer.elapsedNow()

    /// Start a clock now to measure variable definition on the class
    /// Put at the very last to make sure we are not counting the class attribute twice
    private val contentProviderTracker = MonoClock.markNow()
}

@ExperimentalTime
class MainActivity : AppCompatActivity() {

    private val singleTracer = MonoClock.markNow()

    private var activityPreOnCreate: Duration? = null

    override fun attachBaseContext(context: Context) {
        super.attachBaseContext(context)
        // This is the first callback we have that have access to context.
        // We noticed that there is a small gap between the end of Application creation
        // And the start of Activity creation
        val app = context.applicationContext as MyApplication
        activityPreOnCreate = app.activityCreateDelta.elapsedNow() - variablesInit
    }

    override fun onCreate(savedInstanceState: Bundle?) = MonoClock.markNow().also {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }.let {
        val activityOnCreate = it.elapsedNow()
        val app = application as MyApplication
        val appStart = AppStart(
                dexLoad = app.dexLoadTime,
                contentProviderInit = app.contentProviderInit!!,
                applicationClassAttributesInit = app.applicationClassAttributesInit,
                applicationOnCreate = app.applicationOnCreate!!,
                activityPreOnCreate = activityPreOnCreate!!,
                activityClassAttributesInit = variablesInit,
                activityOnCreate = activityOnCreate
        )

        val processTime = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val sumUp = appStart.sumUp()
        reportFullyDrawn() /// Print information from system to compare

        Log.d("APP_START", "Process time $processTime. Calculated: $sumUp")
        Log.d("APP_START", appStart.toString())

        Unit
    }

    /// This need to be the LAST line of code in the class
    private val variablesInit = singleTracer.elapsedNow()
}


@ExperimentalTime
data class AppStart constructor(
        private val dexLoad: Duration,
        private val contentProviderInit: Duration,
        private val applicationClassAttributesInit: Duration,
        private val applicationOnCreate: Duration,
        private val activityPreOnCreate: Duration,
        private val activityClassAttributesInit: Duration,
        private val activityOnCreate: Duration
) {

    fun sumUp() = dexLoad +
            contentProviderInit +
            applicationClassAttributesInit +
            applicationOnCreate +
            activityPreOnCreate +
            activityClassAttributesInit +
            activityOnCreate
}
