package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.ProcessingStatus
import mu.KotlinLogging
import java.util.stream.Collectors
import java.util.stream.Stream

private val logger = KotlinLogging.logger {}
class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {
    /**
     * A list to store failed invoices when [NetworkException] is thrown
     */
    val failedInvoiceList = ArrayList<Invoice>();



    /**
     * Method invokes on {@link invoiceService#fetchAll} to retrieve list of
     * invoices. Invoices are being processed to retrieve invoices with
     * pending status. {@link PaymentProvider#charge} method is invoked on invoices with pending status.
     *
     */
    fun processSubscription() {
        val invoiceList = invoiceService.fetchAll()
        invoiceList.forEach {
            //I've added a field `processingStatus` which determines if a customer has been charged a subscription/not
            if(it.processingStatus == ProcessingStatus.DEFAULT || it.processingStatus == ProcessingStatus.FAILED) {
                try {
                    if(paymentProvider.charge(it)){
                        it.processingStatus = ProcessingStatus.COMPLETED
                        invoiceService.update(it);
                    }
                }catch (customerEx: CustomerNotFoundException){
                    //Customer not available. per requirements we can store  log report
                    //of unavailable customers  per each month
                    it.processingStatus = ProcessingStatus.NOT_FOUND
                    invoiceService.update(it);
                    logger.info { "Customer ${it.customerId}  not available" }
                }catch (networkEx: NetworkException){
                    //Store uncompleted subscriptions when `NetworkException` is thrown.
                    it.processingStatus = ProcessingStatus.FAILED
                    invoiceService.update(it)
                    logger.error { "Network Exception. Skipping..."}
                    //
                    failedInvoiceList.add(it);
                }
            }
        }
    }

    fun retryInvoiceSubscriptionOperation(){
        //I'm retrying the process again and updating the `failedInvoiceList`
        invoiceStream().map {
            try {
                if(paymentProvider.charge(it)){
                    it.processingStatus = ProcessingStatus.COMPLETED
                    invoiceService.update(it);
                }
            }catch (customerEx:CustomerNotFoundException){
                it.processingStatus = ProcessingStatus.NOT_FOUND
                invoiceService.update(it);
                logger.info { "Customer ${it.customerId}  not available" }
            }catch (networkEx:NetworkException){
                it.processingStatus = ProcessingStatus.FAILED
                invoiceService.update(it)
                logger.error { "Network Exception. Skipping..."}
            }
        }.collect(Collectors.toList());


        // I want to know the number of failed Invoices again
        // to make a decision to send a report to admin
        val numOfFailedInvoices = invoiceStream()
                .filter { e -> e.processingStatus == ProcessingStatus.FAILED }
                .count()

        if(numOfFailedInvoices > 0) {
            //We can send a report to the admin concerning the network failure.
        }

        logger.info { "Number of Invoices Failed: ${numOfFailedInvoices}" }
    }

    /**
     * holds list into a stream source
     * @return stream of invoice
     */
    private fun invoiceStream(): Stream<Invoice> {
        return failedInvoiceList.stream();
    }
}