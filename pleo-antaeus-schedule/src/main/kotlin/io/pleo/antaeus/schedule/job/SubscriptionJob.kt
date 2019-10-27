package io.pleo.antaeus.schedule.job

import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.schedule.service.JobSchedulingService
import mu.KotlinLogging
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import java.time.LocalDateTime
import java.util.concurrent.FutureTask

private val logger = KotlinLogging.logger{}

internal class SubscriptionJob() : Job {
    private var retries = 0

    /**
     *
     *
     * Called by the `[Scheduler]` when a `[Trigger]`
     * fires that is associated with the `Job`.
     *
     *
     *
     *
     * The implementation may wish to set a
     * [result][JobExecutionContext.setResult] object on the
     * [JobExecutionContext] before this method exits.  The result itself
     * is meaningless to Quartz, but may be informative to
     * `[JobListener]s` or
     * `[TriggerListener]s` that are watching the job's
     * execution.
     *
     *
     * @param context
     * @throws JobExecutionException if there is an exception while executing the job.
     */
    @Throws(JobExecutionException::class)
    override fun execute(context: JobExecutionContext) {

        //Process my Task here
        performAsyncTask(context);

    }


    private fun performAsyncTask(jobExecutionContext: JobExecutionContext){
        logger.info { "Subscription Process Started" }
        val context = jobExecutionContext.scheduler.context
        var billingService = context[JobSchedulingService.BILLING_SERVICE] as BillingService
        val futureTask = FutureTask{
            billingService.processSubscription();
        }
        futureTask.run();

        //Task get Completed
        if(futureTask.isDone){
            logger.info { "Job is completed at "+LocalDateTime.now() }

            //Checks if there are failed invoices in the results.
            checkFailedInvoiceByProcessingStatus(billingService);

            //Schedules date for the next month
            scheduleNextJob(billingService);
        }

        //Task Was Cancelled
        if (futureTask.isCancelled){
            retries++;
            logger.info { "Process was cancelled. Retrying again..." }
            //Here I have set the number of retries to 5
            //send a notification
            if(retries == 5){
                //Send a report
                logger.info { "Exceeded maximum number of retries" }
            }else{
                //Retry Task
                performAsyncTask(jobExecutionContext);
            }

        }
    }

    /**
     * Called by the `[JobSchedule.execute]`
     * after a successful job execution to reschedule first date for the next month
     */
    private fun scheduleNextJob(billingService: BillingService) {
        val jobService = JobSchedulingService(billingService)
        jobService.schedule(jobService.nextDate())
        logger.info("Job Scheduled for the next month")
    }

    /**
     * Method is called to cross check if all invoices have been charged with
     * subscriptions. If  [NetworkException] was thrown, method will invoke `[BillingService.retryInvoiceSubscriptionOperation]`
     * to charge subscription on the failed Invoices.
     */
    private fun checkFailedInvoiceByProcessingStatus(billingService:BillingService){
        if(!billingService.failedInvoiceList.isEmpty())
            billingService.retryInvoiceSubscriptionOperation();
    }
}
