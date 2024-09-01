package com.classification.demo.service;

import io.github.stefanbratanov.jvm.openai.*;
import io.github.stefanbratanov.jvm.openai.Thread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class OpenAIService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);

    private static final String ASSISTANT_QUESTION = "What type of insurance document is this? Answer using only one of: ['Policy Document', 'Authorization Letter', 'Loss Run Document', 'Notice of Cancellation', 'Certificate of Insurance', 'UNKNOWN']. If unsure, respond with 'UNKNOWN'. Please explain why you made your decision.";
    private static final String ASSISTANT_PROMPT = """
        You are an insurance document classifier. Your job is to classify PDF documents into one of the following categories:
        {
            "Policy Document": "The policy document is the official contract between the insured (policyholder) and the insurance company. It outlines the terms and conditions of the insurance coverage, including what is covered, what is excluded, the premium amount, the policy period, and any endorsements or riders. This document serves as the legal agreement that governs the insurance relationship.",
            "Authorization Letter": "An authorization letter in insurance is a written document that grants permission to someone (usually an insurance agent, broker, or another third party) to act on behalf of the insured. This could involve handling claims, making inquiries, or accessing personal information related to the insurance policy. The letter typically specifies the scope and duration of the authorization.",
            "Loss Run Document": "A loss run document is a report provided by an insurance company that details the claims history of a policyholder. It includes information on all the claims that have been filed against the policy, the amounts paid out, and the status of any open claims. This document is often requested by new insurers when an individual or business is applying for new coverage to assess the risk and determine premiums.",
            "Notice of Cancellation: "A notice of cancellation (NOC) is a formal notification from an insurance company to the insured of a change to their existing insurance policy towards cancellation or termination. This notice typically includes the effective date of cancellation, the reason for cancellation, and any steps the insured may take to prevent the cancellation (e.g., paying overdue premiums). The notice of cancellation may also be issued by the insured if they wish to terminate their coverage.",
            "Certificate of Insurance: "A certificate of insurance (COI) is a document that provides proof of insurance coverage. It is usually a one-page summary that outlines the key aspects of the policy, such as the insured party, the types of coverage, policy limits, and the policy period. Certificates of insurance are commonly used in business transactions to provide evidence of insurance to third parties, such as clients, vendors, or landlords.",
            "UNKNOWN": "Any document which cannot be categorized into one of the above"
        }
        Respond **only** with the classification category, and no other text. 
        Make use of the description of each document type provided above when making your decision.    
        """;

    private static final double TEMPERATURE = 0.01;
    private static final int SLEEP_DURATION_MS = 100;
    private static final int MAX_RETRIES = 10;
    private static final String VECTOR_STORE_NAME = "Insurance Document Classifier";
    private static final List<String> CATEGORIES = Arrays.asList(
            "Policy Document",
            "Authorization Letter",
            "Loss Run Document",
            "Notice of Cancellation",
            "Certificate of Insurance",
            "UNKNOWN"
    );

    private final OpenAI openAI;

    public OpenAIService(OpenAI openAiClient) {
        this.openAI = openAiClient;
    }

    public String getOpenAIResponse(MultipartFile file) {
        Path tempFile = null;
        try {
            tempFile = createTempFile(file);
            Assistant assistant = createAssistant();
            VectorStore vectorStore = createAndPopulateVectorStore(tempFile);
            updateAssistantWithVectorStore(assistant, vectorStore);
            return classifyDocument(assistant);
        } catch (IOException | InterruptedException e) {
            logger.error("Error occurred while processing the file: ", e);
            return "An error occurred: " + e.getMessage();
        } finally {
            cleanupTempFile(tempFile);
        }
    }

    private Path createTempFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Uploaded file is empty.");
        }
        Path tempFile = Files.createTempFile("upload-", file.getOriginalFilename());
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        long tempFileSize = Files.size(tempFile);
        if (tempFileSize == 0) {
            throw new IOException("Temporary file is empty after copying.");
        }
        logger.info("Temporary file created: {} bytes", tempFileSize);
        return tempFile;
    }

    private Assistant createAssistant() {
        CreateAssistantRequest createAssistantRequest = CreateAssistantRequest.newBuilder()
                .name("Insurance Document Classifier")
                .model(OpenAIModel.GPT_3_5_TURBO)
                .instructions(ASSISTANT_PROMPT)
                .tool(Tool.fileSearchTool())
                .build();
        return openAI.assistantsClient().createAssistant(createAssistantRequest);
    }

    private VectorStore createAndPopulateVectorStore(Path tempFile) throws IOException, InterruptedException {
        VectorStoresClient vectorStoresClient = openAI.vectorStoresClient();
        FilesClient filesClient = openAI.filesClient();
        VectorStoreFilesClient vectorStoreFilesClient = openAI.vectorStoreFilesClient();

        VectorStore vectorStore = createVectorStore(vectorStoresClient);
        File uploadedFile = uploadFile(tempFile, filesClient);
        VectorStoreFile vectorStoreFile = addToVectorStore(vectorStore, uploadedFile, vectorStoreFilesClient);

        waitForProcessing(vectorStoreFilesClient, vectorStore, vectorStoreFile);

        return vectorStore;
    }

    private VectorStore createVectorStore(VectorStoresClient vectorStoresClient) {
        CreateVectorStoreRequest createVectorStoreRequest = CreateVectorStoreRequest.newBuilder()
                .name(VECTOR_STORE_NAME)
                .build();
        return vectorStoresClient.createVectorStore(createVectorStoreRequest);
    }

    private File uploadFile(Path tempFile, FilesClient filesClient) throws IOException {
        UploadFileRequest uploadFileRequest = UploadFileRequest.newBuilder()
                .file(tempFile)
                .purpose(Purpose.ASSISTANTS)
                .build();
        return filesClient.uploadFile(uploadFileRequest);
    }

    private VectorStoreFile addToVectorStore(VectorStore vectorStore, File uploadedFile, VectorStoreFilesClient vectorStoreFilesClient) {
        CreateVectorStoreFileRequest createVectorStoreFileRequest = CreateVectorStoreFileRequest.newBuilder()
                .fileId(uploadedFile.id())
                .build();
        return vectorStoreFilesClient.createVectorStoreFile(vectorStore.id(), createVectorStoreFileRequest);
    }

    private void waitForProcessing(VectorStoreFilesClient vectorStoreFilesClient, VectorStore vectorStore, VectorStoreFile vectorStoreFile) throws InterruptedException {
        String vectorStoreFileStatus;
        do {
            java.lang.Thread.sleep(SLEEP_DURATION_MS);
            vectorStoreFile = vectorStoreFilesClient.retrieveVectorStoreFile(vectorStore.id(), vectorStoreFile.id());
            vectorStoreFileStatus = vectorStoreFile.status();
        } while ("processing".equals(vectorStoreFileStatus));
    }

    private void updateAssistantWithVectorStore(Assistant assistant, VectorStore vectorStore) {
        ModifyAssistantRequest modifyAssistantRequest = ModifyAssistantRequest.newBuilder()
                .toolResources(ToolResources.fileSearchToolResources(vectorStore.id()))
                .build();
        openAI.assistantsClient().modifyAssistant(assistant.id(), modifyAssistantRequest);
    }

    private String classifyDocument(Assistant assistant) throws InterruptedException {
        ThreadsClient threadsClient = openAI.threadsClient();
        MessagesClient messagesClient = openAI.messagesClient();
        RunsClient runsClient = openAI.runsClient();

        Thread thread = createThread(threadsClient);
        ThreadRun run = createRun(assistant, threadsClient, runsClient, thread);

        waitForRunCompletion(runsClient, thread, run);

        List<ThreadMessage> messages = retrieveMessagesWithRetries(messagesClient, thread);

        if (messages.size() < 2) {
            return "Failed to get the required number of messages.";
        }

        return extractCategory(messages.get(0).toString());
    }

    private Thread createThread(ThreadsClient threadsClient) {
        CreateThreadRequest.Message message = CreateThreadRequest.Message.newBuilder()
                .role(Role.USER)
                .content(ASSISTANT_QUESTION)
                .build();
        return threadsClient.createThread(CreateThreadRequest.newBuilder().message(message).build());
    }

    private ThreadRun createRun(Assistant assistant, ThreadsClient threadsClient, RunsClient runsClient, Thread thread) {
        return runsClient.createRun(thread.id(), CreateRunRequest.newBuilder()
                .assistantId(assistant.id())
                .temperature(TEMPERATURE)
                .build());
    }

    private void waitForRunCompletion(RunsClient runsClient, Thread thread, ThreadRun run) throws InterruptedException {
        String runStatus;
        do {
            java.lang.Thread.sleep(SLEEP_DURATION_MS);
            run = runsClient.retrieveRun(thread.id(), run.id());
            runStatus = run.status();
            logger.info("Run Status: {}", runStatus);
            if ("failed".equals(runStatus)) {
                logger.error("Run Failed: {}", run.lastError());
                throw new RuntimeException("Run failed: " + run.lastError());
            }
        } while ("in_progress".equals(runStatus));
    }

    private List<ThreadMessage> retrieveMessagesWithRetries(MessagesClient messagesClient, Thread thread) throws InterruptedException {
        List<ThreadMessage> messages;
        int retryCount = 0;
        do {
            messages = messagesClient.listMessages(thread.id(), PaginationQueryParameters.newBuilder().order("desc").build(), Optional.empty()).data();

            if (messages.size() < 2) {
                java.lang.Thread.sleep(SLEEP_DURATION_MS);
                retryCount++;
            }

            if (retryCount >= MAX_RETRIES) {
                throw new RuntimeException("Failed to get the required number of messages.");
            }

        } while (messages.size() < 2);
        return messages;
    }

    private static String extractCategory(String response) {
        logger.info("response: {}", response);
        Optional<String> category = CATEGORIES.stream()
                .filter(response::contains)
                .findFirst();

        return category.orElse("UNKNOWN");
    }

    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                logger.error("Error cleaning up temporary file: ", e);
            }
        }
    }
}
