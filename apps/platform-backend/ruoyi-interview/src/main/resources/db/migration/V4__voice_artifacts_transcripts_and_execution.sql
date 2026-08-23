-- PostgreSQL 只保存音频元数据和加密 opaque object reference；不保存原始音频正文。
create table voice.audio_artifact (
    tenant_id varchar(128) not null,
    artifact_id varchar(128) not null,
    session_id varchar(128) not null,
    turn_id varchar(128) not null,
    purpose varchar(32) not null check (purpose in ('ANSWER_TRANSCRIPTION','TTS_PLAYBACK')),
    consent_record_id varchar(128) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    state varchar(32) not null check (state in (
        'CREATED','UPLOADING','UPLOADED','TRANSCRIBING','TRANSCRIBED',
        'SYNTHESIZING','SYNTHESIZED','UPLOAD_FAILED','TRANSCRIBE_FAILED',
        'SYNTHESIS_FAILED','DELETE_QUEUED','DELETE_PARTIAL','DELETED')),
    codec varchar(128),
    sample_rate integer check (sample_rate is null or sample_rate > 0),
    channel_count integer check (channel_count is null or channel_count between 1 and 8),
    byte_count bigint check (byte_count is null or byte_count > 0),
    duration_millis bigint check (duration_millis is null or duration_millis > 0),
    object_key_id varchar(255),
    object_algorithm varchar(64),
    object_nonce bytea,
    object_ciphertext bytea,
    object_aad_hash varchar(128),
    content_hash varchar(128),
    provider_invocation_id varchar(128),
    delete_queued_at timestamptz,
    deleted_at timestamptz,
    failure_code varchar(128),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, artifact_id),
    unique (tenant_id, session_id, turn_id, artifact_id),
    foreign key (tenant_id, session_id, turn_id)
        references interview.turn(tenant_id, session_id, turn_id),
    foreign key (tenant_id, consent_record_id)
        references governance.consent_record(tenant_id, consent_record_id),
    check (expires_at > created_at),
    check ((object_key_id is null) = (object_algorithm is null)
        and (object_key_id is null) = (object_nonce is null)
        and (object_key_id is null) = (object_ciphertext is null)
        and (object_key_id is null) = (object_aad_hash is null)),
    check ((state = 'DELETED') = (deleted_at is not null)),
    check ((state in ('DELETE_QUEUED','DELETE_PARTIAL','DELETED')) = (delete_queued_at is not null)),
    check ((state in ('UPLOAD_FAILED','TRANSCRIBE_FAILED','SYNTHESIS_FAILED','DELETE_PARTIAL'))
        = (failure_code is not null))
);

create index audio_deletion_due_idx on voice.audio_artifact (
    expires_at, tenant_id, artifact_id) where state <> 'DELETED';

create table voice.transcript (
    tenant_id varchar(128) not null,
    transcript_id varchar(128) not null,
    session_id varchar(128) not null,
    turn_id varchar(128) not null,
    audio_artifact_id varchar(128) not null,
    state varchar(16) not null check (state in ('OPEN','ASR_FINAL','CONFIRMED','CANCELLED')),
    confirmed_version_id varchar(128),
    confirmed_by_ruoyi_user_id bigint,
    confirmed_at timestamptz,
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, transcript_id),
    unique (tenant_id, session_id, turn_id),
    unique (tenant_id, session_id, turn_id, transcript_id),
    foreign key (tenant_id, session_id, turn_id, audio_artifact_id)
        references voice.audio_artifact(tenant_id, session_id, turn_id, artifact_id),
    foreign key (tenant_id, session_id, turn_id)
        references interview.turn(tenant_id, session_id, turn_id),
    foreign key (tenant_id, confirmed_by_ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id),
    check ((state = 'CONFIRMED') = (confirmed_version_id is not null)),
    check ((confirmed_version_id is null) = (confirmed_by_ruoyi_user_id is null)
        and (confirmed_version_id is null) = (confirmed_at is null))
);

create table voice.transcript_version (
    tenant_id varchar(128) not null,
    transcript_version_id varchar(128) not null,
    transcript_id varchar(128) not null,
    version_no integer not null check (version_no > 0),
    source varchar(32) not null check (source in ('ASR','USER_CORRECTION')),
    content_hash varchar(128) not null,
    body_key_id varchar(255) not null,
    body_algorithm varchar(64) not null,
    body_nonce bytea not null,
    body_ciphertext bytea not null,
    body_aad_hash varchar(128) not null,
    language varchar(35) not null,
    offset_unit varchar(32) not null check (offset_unit in ('UTF16','UNICODE_CODE_POINT')),
    provider_invocation_id varchar(128),
    corrected_by_ruoyi_user_id bigint,
    supersedes_id varchar(128),
    created_at timestamptz not null,
    primary key (tenant_id, transcript_version_id),
    unique (tenant_id, transcript_id, version_no),
    unique (tenant_id, transcript_id, transcript_version_id),
    foreign key (tenant_id, transcript_id)
        references voice.transcript(tenant_id, transcript_id),
    foreign key (tenant_id, corrected_by_ruoyi_user_id)
        references platform.business_membership(tenant_id, ruoyi_user_id),
    foreign key (tenant_id, transcript_id, supersedes_id)
        references voice.transcript_version(tenant_id, transcript_id, transcript_version_id),
    check ((source = 'ASR') = (provider_invocation_id is not null)),
    check ((source = 'USER_CORRECTION') = (corrected_by_ruoyi_user_id is not null)),
    check (supersedes_id is null or supersedes_id <> transcript_version_id)
);

alter table voice.transcript add constraint transcript_confirmed_version_fk
    foreign key (tenant_id, transcript_id, confirmed_version_id)
    references voice.transcript_version(tenant_id, transcript_id, transcript_version_id)
    deferrable initially deferred;

alter table interview.answer_version add constraint interview_answer_transcript_version_fk
    foreign key (tenant_id, transcript_version_id)
    references voice.transcript_version(tenant_id, transcript_version_id)
    deferrable initially deferred;

create function voice.guard_interview_answer_transcript_scope()
returns trigger
language plpgsql
as $$
begin
    if new.transcript_version_id is not null and not exists (
        select 1
          from voice.transcript_version tv
          join voice.transcript t
            on t.tenant_id = tv.tenant_id and t.transcript_id = tv.transcript_id
         where tv.tenant_id = new.tenant_id
           and tv.transcript_version_id = new.transcript_version_id
           and t.session_id = new.session_id
           and t.turn_id = new.turn_id
    ) then
        raise exception 'interview answer transcript version belongs to another turn'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create constraint trigger interview_answer_transcript_scope
after insert or update on interview.answer_version
deferrable initially deferred
for each row execute function voice.guard_interview_answer_transcript_scope();

create table voice.transcript_confidence_span (
    tenant_id varchar(128) not null,
    transcript_version_id varchar(128) not null,
    span_no integer not null check (span_no > 0),
    start_inclusive integer not null check (start_inclusive >= 0),
    end_exclusive integer not null,
    confidence numeric(9,8) not null check (confidence between 0 and 1),
    primary key (tenant_id, transcript_version_id, span_no),
    foreign key (tenant_id, transcript_version_id)
        references voice.transcript_version(tenant_id, transcript_version_id),
    check (end_exclusive > start_inclusive)
);

create table voice.turn_execution (
    tenant_id varchar(128) not null,
    execution_id varchar(128) not null,
    session_id varchar(128) not null,
    turn_id varchar(128) not null,
    state varchar(32) not null check (state in (
        'IDLE','LISTENING','TRANSCRIBING','CONFIRMING','THINKING','SPEAKING','DEGRADED','CANCELLED')),
    input_artifact_id varchar(128),
    transcript_id varchar(128),
    confirmed_transcript_version_id varchar(128),
    output_artifact_id varchar(128),
    socket_generation bigint not null check (socket_generation >= 0),
    last_client_sequence bigint not null check (last_client_sequence >= 0),
    last_server_sequence bigint not null check (last_server_sequence >= 0),
    degradation_reason varchar(128),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, execution_id),
    unique (tenant_id, session_id, turn_id),
    foreign key (tenant_id, session_id, turn_id)
        references interview.turn(tenant_id, session_id, turn_id),
    foreign key (tenant_id, session_id, turn_id, input_artifact_id)
        references voice.audio_artifact(tenant_id, session_id, turn_id, artifact_id),
    foreign key (tenant_id, session_id, turn_id, output_artifact_id)
        references voice.audio_artifact(tenant_id, session_id, turn_id, artifact_id),
    foreign key (tenant_id, session_id, turn_id, transcript_id)
        references voice.transcript(tenant_id, session_id, turn_id, transcript_id),
    foreign key (tenant_id, transcript_id, confirmed_transcript_version_id)
        references voice.transcript_version(tenant_id, transcript_id, transcript_version_id),
    check ((state = 'DEGRADED') = (degradation_reason is not null))
);

create trigger transcript_version_immutable
before update or delete on voice.transcript_version
for each row execute function platform.reject_immutable_row_mutation();

create trigger transcript_confidence_span_immutable
before update or delete on voice.transcript_confidence_span
for each row execute function platform.reject_immutable_row_mutation();
