-- 새 빈 DB를 선택한 뒤 최초 1회 수동 실행하세요. 기존 DB에는 실행하지 마세요.
-- 더미 데이터와 Flyway 의존성은 없으며, 서비스 시작 시 자동 실행되지 않습니다.
-- Chapchap Customer - initial schema for a NEW, EMPTY MySQL 8 database.
-- Run manually after selecting the service database: SOURCE sql/schema.sql;
-- Never run against an existing database. No seed data, database creation or cleanup.
-- This file is outside classpath resources and is not automatically executed.
-- Foreign keys reflect JPA relationships only; other service IDs remain logical references.
-- No cascading deletes. Timestamps/default values are supplied by the application.
-- Binary collation keeps opaque identifiers and unique keys case-sensitive.
-- ddl-auto=validate remains enabled. Later changes require separate reviewed ALTER SQL.

create table audit_logs (
    actor_user_id bigint,
    audit_log_id bigint not null auto_increment,
    created_at datetime(6) not null,
    target_id varchar(64),
    trace_id varchar(64),
    action_type enum ('AI_SUMMARY_FAILED','AI_SUMMARY_GENERATED','CONSULTATION_ACCEPTED','CONSULTATION_CLOSED','CONSULTATION_ESCALATED','FAQ_CREATED','FAQ_DEACTIVATED','FAQ_UPDATED','KNOWLEDGE_PROCESSING_FAILED','KNOWLEDGE_VERSION_ACTIVATED','KNOWLEDGE_VERSION_REGISTERED','QUALITY_INQUIRY_PROCESSED') not null,
    actor_type enum ('ADMIN','AI','SYSTEM','USER') not null,
    detail JSON,
    result enum ('BLOCKED','FAILURE','SUCCESS') not null,
    target_type enum ('CONSULTATION','FAQ','KNOWLEDGE_VERSION','QUALITY_INQUIRY') not null,
    primary key (audit_log_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table consultation_message_sources (
    retrieval_rank integer not null,
    similarity_score decimal(6,5) not null,
    created_at datetime(6) not null,
    knowledge_version_id bigint not null,
    message_id bigint not null,
    message_source_id bigint not null auto_increment,
    source_chunk_id varchar(255) not null,
    primary key (message_source_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table consultation_messages (
    sequence_no integer not null,
    consultation_id bigint not null,
    created_at datetime(6) not null,
    message_id bigint not null auto_increment,
    sender_user_id bigint,
    trigger_message_id bigint,
    content TEXT not null,
    sender_type enum ('ADMIN','AI','USER') not null,
    primary key (message_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table consultation_summaries (
    consultation_id bigint not null,
    created_at datetime(6) not null,
    reviewed_at datetime(6),
    reviewed_by_user_id bigint,
    summary_id bigint not null auto_increment,
    updated_at datetime(6) not null,
    ai_summary TEXT,
    final_summary TEXT,
    primary key (summary_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table consultation_summary_jobs (
    is_retryable bit not null,
    accepted_at datetime(6),
    callback_received_at datetime(6),
    consultation_id bigint not null,
    consultation_summary_job_id bigint not null auto_increment,
    created_at datetime(6) not null,
    submitted_at datetime(6),
    updated_at datetime(6) not null,
    request_id varchar(36) not null,
    terminal_fingerprint varchar(64),
    failure_code varchar(100),
    status enum ('ACCEPTED','COMPLETED','FAILED','PENDING','SUBMISSION_FAILED','SUBMITTED') not null,
    primary key (consultation_summary_job_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table consultations (
    assigned_admin_id bigint,
    assigned_at datetime(6),
    closed_at datetime(6),
    consultation_id bigint not null auto_increment,
    created_at datetime(6) not null,
    escalated_at datetime(6),
    updated_at datetime(6) not null,
    user_id bigint not null,
    priority varchar(20),
    category varchar(40),
    intent varchar(100),
    status enum ('AI_HANDLING','CLOSED','IN_PROGRESS','WAITING_ADMIN') not null,
    primary key (consultation_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table cs_read_models (
    is_delayed bit,
    business_version bigint not null,
    occurred_at datetime(6) not null,
    read_model_id bigint not null auto_increment,
    synced_at datetime(6) not null,
    user_id bigint not null,
    source_event_id varchar(36) not null,
    status varchar(50) not null,
    aggregate_id varchar(64) not null,
    projection_type enum ('DELIVERY','DELIVERY_ADDRESS','PAYMENT','SUBSCRIPTION') not null,
    primary key (read_model_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table faqs (
    display_order integer not null,
    is_active bit not null,
    is_published bit not null,
    created_at datetime(6) not null,
    created_by_user_id bigint not null,
    faq_id bigint not null auto_increment,
    updated_at datetime(6) not null,
    updated_by_user_id bigint,
    category varchar(40) not null,
    question varchar(500) not null,
    answer TEXT not null,
    primary key (faq_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table knowledge_documents (
    created_at datetime(6) not null,
    knowledge_document_id bigint not null auto_increment,
    updated_at datetime(6) not null,
    category varchar(40) not null,
    source_service varchar(50) not null,
    document_key varchar(100) not null,
    title varchar(200) not null,
    primary key (knowledge_document_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table knowledge_processing_attempts (
    attempt integer not null,
    chunk_count integer,
    is_retryable bit not null,
    accepted_at datetime(6),
    callback_received_at datetime(6),
    created_at datetime(6) not null,
    knowledge_processing_attempt_id bigint not null auto_increment,
    knowledge_processing_job_id bigint not null,
    submitted_at datetime(6),
    updated_at datetime(6) not null,
    request_id varchar(36) not null,
    chunk_profile varchar(50),
    terminal_fingerprint varchar(64),
    failure_code enum ('CHUNK_PROFILE_INVALID','CUSTOMER_AI_UNAVAILABLE','EMBEDDING_UNAVAILABLE','ENCRYPTED_DOCUMENT','PROCESSING_TIMEOUT','SOURCE_FETCH_FAILED','TEXT_EXTRACTION_FAILED','UNSUPPORTED_DOCUMENT','VECTOR_STORE_UNAVAILABLE'),
    status enum ('ACCEPTED','CREATED','SUBMISSION_FAILED','SUBMITTED','SUPERSEDED','TERMINAL') not null,
    terminal_status enum ('COMPLETED','FAILED'),
    primary key (knowledge_processing_attempt_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table knowledge_processing_jobs (
    chunk_count integer,
    current_attempt integer not null,
    is_retryable bit not null,
    callback_received_at datetime(6),
    created_at datetime(6) not null,
    knowledge_processing_job_id bigint not null auto_increment,
    knowledge_version_id bigint not null,
    processing_id bigint,
    updated_at datetime(6) not null,
    chunk_profile varchar(50) not null,
    terminal_fingerprint varchar(64),
    failure_code varchar(100),
    status enum ('ACCEPTED','COMPLETED','FAILED','PENDING') not null,
    primary key (knowledge_processing_job_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table knowledge_versions (
    is_active bit not null,
    is_retryable bit not null,
    processing_attempt_count integer not null,
    activated_at datetime(6),
    created_at datetime(6) not null,
    effective_from datetime(6) not null,
    file_size bigint not null,
    knowledge_document_id bigint not null,
    knowledge_version_id bigint not null auto_increment,
    processed_at datetime(6),
    updated_at datetime(6) not null,
    uploaded_by_user_id bigint not null,
    version varchar(30) not null,
    chunk_profile varchar(50) not null,
    content_type varchar(100) not null,
    failure_code varchar(100),
    object_key varchar(255) not null,
    original_filename varchar(255) not null,
    processing_status enum ('FAILED','PROCESSING','READY','UPLOADED') not null,
    primary key (knowledge_version_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table notification_reads (
    notification_id bigint not null,
    notification_read_id bigint not null auto_increment,
    read_at datetime(6) not null,
    reader_user_id bigint not null,
    primary key (notification_read_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table notifications (
    delivery_date date,
    created_at datetime(6) not null,
    notification_id bigint not null auto_increment,
    occurred_at datetime(6) not null,
    recipient_user_id bigint,
    delivery_slot varchar(10),
    reminder_stage varchar(10),
    action_reason varchar(30),
    response_deadline varchar(35),
    source_event_id varchar(36) not null,
    related_type varchar(40),
    related_id varchar(64),
    title varchar(150) not null,
    content varchar(1000) not null,
    business_key varchar(255),
    notification_type enum ('ADMIN_ASSIGNMENT_ACTION_REQUIRED','ADMIN_EVENT_PUBLISH_FAILED','ADMIN_LATE_ORDER_REVIEW','ADMIN_UNRESOLVED_DELIVERY','DELIVERY_ADDRESS_CHANGED','DELIVERY_ADDRESS_CHANGE_REJECTED','DELIVERY_COMPLETED','DELIVERY_CREATED','DELIVERY_DELAYED','DELIVERY_FAILED','DELIVERY_STARTED','FIRST_SUBSCRIPTION_PAYMENT_COMPLETED','REFUND_COMPLETED','REFUND_FAILED','REGULAR_PAYMENT_COMPLETED','REGULAR_PAYMENT_FINAL_FAILED','REGULAR_PAYMENT_RETRY_WAITING','RIDER_ACKNOWLEDGEMENT_OPENED','RIDER_ACKNOWLEDGEMENT_REMINDER','RIDER_ASSIGNMENT_AVAILABLE','RIDER_REASSIGNED','SETTING_CHANGE_PAYMENT_COMPLETED','SETTING_CHANGE_PAYMENT_FAILED','SUBSCRIPTION_CANCELLATION_CONFIRMED','SUBSCRIPTION_ENDED','SUBSCRIPTION_SETTING_CHANGED') not null,
    recipient_type enum ('ADMIN','CUSTOMER','RIDER') not null,
    primary key (notification_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table quality_inquiries (
    assigned_admin_id bigint,
    closed_at datetime(6),
    created_at datetime(6) not null,
    order_id bigint,
    product_id bigint,
    quality_inquiry_id bigint not null auto_increment,
    resolved_at datetime(6),
    updated_at datetime(6) not null,
    user_id bigint not null,
    priority varchar(20),
    delivery_id varchar(64),
    admin_answer TEXT,
    content TEXT not null,
    summary TEXT,
    inquiry_type enum ('DAMAGED','DELIVERY','MISSING','OTHER','QUALITY') not null,
    status enum ('CLOSED','IN_PROGRESS','RECEIVED','RESOLVED') not null,
    primary key (quality_inquiry_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

create table quality_inquiry_attachments (
    attachment_id bigint not null auto_increment,
    created_at datetime(6) not null,
    file_size bigint not null,
    quality_inquiry_id bigint not null,
    content_type varchar(100) not null,
    object_key varchar(255) not null,
    original_filename varchar(255) not null,
    primary key (attachment_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin;

alter table consultation_message_sources
   add constraint uk_consultation_message_sources_chunk unique (message_id, source_chunk_id);

alter table consultation_messages
   add constraint uk_consultation_messages_sequence unique (consultation_id, sequence_no);

alter table consultation_messages
   add constraint uk_consultation_messages_trigger unique (trigger_message_id);

alter table consultation_summaries
   add constraint uk_consultation_summaries_consultation unique (consultation_id);

alter table consultation_summary_jobs
   add constraint uk_consultation_summary_jobs_consultation unique (consultation_id);

alter table consultation_summary_jobs
   add constraint uk_consultation_summary_jobs_request unique (request_id);

alter table cs_read_models
   add constraint uk_cs_read_models_projection unique (projection_type, aggregate_id);

alter table knowledge_documents
   add constraint uk_knowledge_documents_source_service_document_key unique (source_service, document_key);

alter table knowledge_processing_attempts
   add constraint uk_knowledge_processing_attempts_job_attempt unique (knowledge_processing_job_id, attempt);

alter table knowledge_processing_attempts
   add constraint uk_knowledge_processing_attempts_request unique (request_id);

alter table knowledge_processing_jobs
   add constraint uk_knowledge_processing_jobs_version unique (knowledge_version_id);

alter table knowledge_processing_jobs
   add constraint uk_knowledge_processing_jobs_processing unique (processing_id);

alter table knowledge_versions
   add constraint uk_knowledge_versions_document_version unique (knowledge_document_id, version);

alter table knowledge_versions
   add constraint uk_knowledge_versions_object_key unique (object_key);

alter table notification_reads
   add constraint uk_notification_reads_reader unique (notification_id, reader_user_id);

alter table notifications
   add constraint uk_notifications_source_event unique (source_event_id);

alter table notifications
   add constraint uk_notifications_business_key unique (business_key);

alter table quality_inquiry_attachments
   add constraint uk_quality_inquiry_attachments_object_key unique (object_key);

alter table consultation_message_sources
   add constraint fk_consultation_message_sources_knowledge_version_id foreign key (knowledge_version_id)
   references knowledge_versions (knowledge_version_id);

alter table consultation_message_sources
   add constraint fk_consultation_message_sources_message_id foreign key (message_id)
   references consultation_messages (message_id);

alter table consultation_messages
   add constraint fk_consultation_messages_consultation_id foreign key (consultation_id)
   references consultations (consultation_id);

alter table notification_reads
   add constraint fk_notification_reads_notification_id foreign key (notification_id)
   references notifications (notification_id);

alter table quality_inquiry_attachments
   add constraint fk_quality_inquiry_attachments_quality_inquiry_id foreign key (quality_inquiry_id)
   references quality_inquiries (quality_inquiry_id);
