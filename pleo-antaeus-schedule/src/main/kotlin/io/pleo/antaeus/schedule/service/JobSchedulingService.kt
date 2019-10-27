package io.pleo.antaeus.schedule.service

import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.schedule.job.SubscriptionJob
import mu.KotlinLogging
import org.quartz.*
import org.quartz.JobBuilder.newJob
import org.quartz.impl.StdSchedulerFactory

import java.time.ZonedDateTime
import java.util.Date
import java.util.UUID

private val logger = KotlinLogging.logger {}
class JobSchedulingService( val billingService: BillingService) : JobSchedule{

    companion object {
        private var scheduler: Scheduler? = null
        const val BILLING_SERVICE = "billingService"
    }

    init {
            try {
                scheduler = StdSchedulerFactory().scheduler
                scheduler!!.context[BILLING_SERVICE] = billingService;
                if(!scheduler!!.isStarted) scheduler!!.start()
            } catch (e: SchedulerException) {
                e.printStackTrace()
            }
    }


    private fun jobDetail(): JobDetail {
        return newJob().ofType(SubscriptionJob::class.java)
                .withIdentity("Invoice Subsription - ${UUID.randomUUID().toString()}")
                .build()
    }


    private fun trigger(zonedDateTime: ZonedDateTime): Trigger {
        return TriggerBuilder.newTrigger()
                .withIdentity("Job-" + UUID.randomUUID().toString())
                .startAt(Date.from(zonedDateTime.toInstant()))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build()
    }

    @Throws(SchedulerException::class)
    override fun schedule(zonedDateTime: ZonedDateTime) {
        scheduler!!.scheduleJob(jobDetail(), trigger(zonedDateTime))
        logger.info { "Schedule for ${zonedDateTime}" }
    }


}




