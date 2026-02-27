package com.ai4dev.tinderfordogs.ai.finetuning.service

import com.ai4dev.tinderfordogs.ai.finetuning.model.FileUploadResponse
import com.ai4dev.tinderfordogs.ai.finetuning.model.FineTuningJob
import org.springframework.http.HttpEntity
import org.springframework.http.MediaType
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface OpenAIService {
    @PostExchange("/files", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
    fun uploadFiles(
        @RequestBody request: MultiValueMap<String, HttpEntity<*>>,
    ): FileUploadResponse

    @PostExchange("/fine_tuning/jobs")
    fun createFineTuningJob(
        @RequestBody request: Map<String, *>,
    ): FineTuningJob

    @GetExchange("/fine_tuning/jobs/{jobId}")
    fun getFineTuningJob(
        @PathVariable jobId: String,
    ): FineTuningJob
}
