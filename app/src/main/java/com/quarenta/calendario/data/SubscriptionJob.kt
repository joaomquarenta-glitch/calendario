package com.quarenta.calendario.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** Every few hours, with internet, refreshes the subscribed calendar links. */
class SubscriptionJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            runCatching { Subscriptions.syncDue(applicationContext, maxAgeMs = 3 * 3600_000L) }
            runCatching { com.quarenta.calendario.widget.NextWidget.refresh(applicationContext) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val ID = 4242

        /** Starts the job when there are links, stops it when there are none. Safe to call often. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (Subscriptions.all(context).isEmpty()) { scheduler.cancel(ID); return }
            if (scheduler.getPendingJob(ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(ID, ComponentName(context, SubscriptionJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(6 * 3600_000L)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
