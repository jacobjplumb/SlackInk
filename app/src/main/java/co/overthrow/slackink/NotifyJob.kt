package co.overthrow.slackink

import android.app.job.JobParameters
import android.app.job.JobService

class NotifyJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                Notifier.check(applicationContext)
            } catch (e: Exception) {
                // Try again at the next scheduled run.
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
