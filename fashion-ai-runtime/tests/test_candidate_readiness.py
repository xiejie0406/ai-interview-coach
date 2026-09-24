from __future__ import annotations

import hashlib
import json
from datetime import UTC, datetime
from decimal import Decimal
from pathlib import Path

import pytest

from fashion_ai.evaluation.candidate_readiness import (
    TECHNICAL_GATE_IDS,
    UAT_SCENARIO_IDS,
    CandidatePreflightReport,
    CandidateReadinessEvidence,
    EvidenceGate,
    UatDecision,
    VisualTrialReport,
    evaluate_candidate_readiness,
    main,
    verify_evidence_files,
)

AUTHORIZATION_HASH = "a" * 64
EVIDENCE_HASH = "b" * 64


def _gate(gate_id: str, evidence_hash: str = EVIDENCE_HASH) -> EvidenceGate:
    return EvidenceGate(
        gate_id=gate_id,
        result="pass",
        evidence_ref=f"evidence://{gate_id}",
        evidence_file="gate-evidence.json",
        evidence_sha256=evidence_hash,
        executed_by="candidate-tester",
        executed_at=datetime(2026, 9, 13, tzinfo=UTC),
    )


def _preflight(
    authorization_hash: str = AUTHORIZATION_HASH,
) -> CandidatePreflightReport:
    return CandidatePreflightReport.model_validate(
        {
            "schemaVersion": "1.0",
            "sourceManifestSha256": authorization_hash,
            "ready": True,
            "gaps": [],
            "trialPolicy": {
                "feeCapCny": Decimal("20.00"),
                "minimumDeliverableTasksPerCategory": 8,
            },
            "safeguards": {
                "noNetworkPerformed": True,
                "noDatabaseWritePerformed": True,
                "noProviderCallPerformed": True,
                "secretValuesAccepted": False,
                "releaseExcluded": True,
                "expectedFashionTableCount": 16,
            },
        }
    )


def _visual(authorization_hash: str = AUTHORIZATION_HASH) -> VisualTrialReport:
    return VisualTrialReport.model_validate(
        {
            "schemaVersion": "1.0",
            "authorizationManifestSha256": authorization_hash,
            "decision": "pass",
            "population": {"expectedTasks": 40, "reportedTasks": 40},
            "perTier": {
                tier: {
                    "submittedTasks": 10,
                    "finalDeliverableTasks": 8,
                    "qualityGatePassed": True,
                }
                for tier in (
                    "one_category",
                    "two_category",
                    "three_category",
                    "four_category",
                )
            },
            "metrics": {"feeCapCny": Decimal("20.000000")},
            "billing": {"allSettled": True},
            "safeguards": {
                "allExpectedTasksPresent": True,
                "failuresRemainInDenominator": True,
                "noNetworkPerformed": True,
                "noProviderCallPerformed": True,
                "releaseDecisionIncluded": False,
            },
            "stopConditionsTriggered": [],
        }
    )


def _evidence(
    evidence_hash: str = EVIDENCE_HASH,
    authorization_hash: str = AUTHORIZATION_HASH,
) -> CandidateReadinessEvidence:
    return CandidateReadinessEvidence(
        schema_version="1.0",
        authorization_manifest_sha256=authorization_hash,
        technical_gates=[
            _gate(gate_id, evidence_hash) for gate_id in sorted(TECHNICAL_GATE_IDS)
        ],
        uat_scenarios=[
            _gate(gate_id, evidence_hash) for gate_id in sorted(UAT_SCENARIO_IDS)
        ],
        uat_decision=UatDecision(
            decision="accepted",
            decided_by="business-owner",
            business_role="业务负责人",
            decided_at=datetime(2026, 9, 13, tzinfo=UTC),
        ),
        release_authorized=False,
    )


def test_readiness_requires_every_real_gate_but_never_authorizes_release() -> None:
    report = evaluate_candidate_readiness(
        _preflight(),
        _visual(),
        _evidence(),
        evidence_files_verified=True,
        authorization_manifest_verified=True,
    )

    assert report["releaseReady"] is True
    assert report["reasons"] == []
    assert report["technicalGateCount"] == 7
    assert report["uatScenarioCount"] == 6
    assert report["releaseAuthorized"] is False
    assert report["releaseStatus"] == "NotReleased"


def test_readiness_rejects_not_run_technical_gate() -> None:
    evidence = _evidence()
    evidence.technical_gates[0] = EvidenceGate(
        gate_id=evidence.technical_gates[0].gate_id,
        result="not_run",
    )

    report = evaluate_candidate_readiness(
        _preflight(),
        _visual(),
        evidence,
        evidence_files_verified=True,
        authorization_manifest_verified=True,
    )

    assert report["releaseReady"] is False
    assert report["reasons"] == [
        f"technical_gate_{evidence.technical_gates[0].gate_id}_not_run"
    ]


def test_readiness_rejects_blocked_visual_trial_and_unsettled_billing() -> None:
    visual = _visual().model_copy(
        update={
            "decision": "blocked",
            "billing": _visual().billing.model_copy(update={"all_settled": False}),
            "stop_conditions_triggered": [{"code": "unknown_unresolved"}],
        }
    )

    report = evaluate_candidate_readiness(
        _preflight(),
        visual,
        _evidence(),
        evidence_files_verified=True,
        authorization_manifest_verified=True,
    )

    assert report["releaseReady"] is False
    assert report["reasons"] == [
        "visual_trial_blocked",
        "visual_trial_billing_unsettled",
        "visual_trial_stop_condition_triggered",
    ]


def test_readiness_rejects_hash_mismatch_and_conditional_uat_acceptance() -> None:
    evidence = _evidence().model_copy(
        update={
            "authorization_manifest_sha256": "c" * 64,
            "uat_decision": UatDecision(
                decision="accepted_with_conditions",
                decided_by="business-owner",
                business_role="业务负责人",
                decided_at=datetime(2026, 9, 13, tzinfo=UTC),
                conditions=["完成候选环境恢复复测"],
            ),
        }
    )

    report = evaluate_candidate_readiness(
        _preflight(),
        _visual(),
        evidence,
        evidence_files_verified=True,
        authorization_manifest_verified=True,
    )

    assert report["releaseReady"] is False
    assert report["reasons"] == [
        "authorization_manifest_hash_mismatch",
        "uat_decision_accepted_with_conditions",
    ]


def test_readiness_rejects_visual_fee_cap_not_approved_by_manifest() -> None:
    visual = _visual().model_copy(
        update={
            "metrics": _visual().metrics.model_copy(
                update={"fee_cap_cny": Decimal("200.00")}
            )
        }
    )

    report = evaluate_candidate_readiness(
        _preflight(),
        visual,
        _evidence(),
        evidence_files_verified=True,
        authorization_manifest_verified=True,
    )

    assert report["releaseReady"] is False
    assert report["reasons"] == ["visual_trial_fee_cap_mismatch"]


def test_readiness_input_safeguards_cannot_drift() -> None:
    preflight_payload = _preflight().model_dump(by_alias=True)
    preflight_payload["safeguards"]["expectedFashionTableCount"] = 44
    visual_payload = _visual().model_dump(by_alias=True)
    visual_payload["safeguards"]["failuresRemainInDenominator"] = False

    with pytest.raises(ValueError):
        CandidatePreflightReport.model_validate(preflight_payload)
    with pytest.raises(ValueError):
        VisualTrialReport.model_validate(visual_payload)


def test_readiness_rejects_missing_uat_scenario() -> None:
    payload = _evidence().model_dump()
    payload["uat_scenarios"] = payload["uat_scenarios"][:-1]

    try:
        CandidateReadinessEvidence.model_validate(payload)
    except ValueError as exception:
        assert "UAT-01～UAT-06" in str(exception)
    else:
        raise AssertionError("缺少 UAT 场景时必须拒绝")


def test_evidence_identity_and_timestamps_must_be_unambiguous() -> None:
    gate_payload = _gate("migration_16_tables").model_dump()
    uat_payload = _evidence().uat_decision.model_dump()
    invalid_payloads = (
        (EvidenceGate, {**gate_payload, "executed_by": "   "}),
        (
            EvidenceGate,
            {**gate_payload, "executed_at": datetime(2026, 9, 13)},
        ),
        (UatDecision, {**uat_payload, "business_role": "   "}),
        (
            UatDecision,
            {**uat_payload, "decided_at": datetime(2026, 9, 13)},
        ),
        (
            UatDecision,
            {
                **uat_payload,
                "decision": "accepted_with_conditions",
                "conditions": ["   "],
            },
        ),
        (UatDecision, {**uat_payload, "conditions": ["待补恢复复测"]}),
    )

    for model, payload in invalid_payloads:
        with pytest.raises(ValueError):
            model.model_validate(payload)


def test_readiness_cannot_pass_before_evidence_files_are_verified() -> None:
    report = evaluate_candidate_readiness(
        _preflight(),
        _visual(),
        _evidence(),
        authorization_manifest_verified=True,
    )

    assert report["releaseReady"] is False
    assert report["reasons"] == ["technical_evidence_files_not_verified"]
    assert report["safeguards"]["technicalEvidenceFilesVerified"] is False


def test_readiness_cannot_pass_without_actual_authorization_manifest() -> None:
    report = evaluate_candidate_readiness(
        _preflight(), _visual(), _evidence(), evidence_files_verified=True
    )

    assert report["releaseReady"] is False
    assert report["reasons"] == ["authorization_manifest_not_verified"]
    assert report["safeguards"]["authorizationManifestVerified"] is False


@pytest.mark.parametrize("invalid_path", ["../outside.json", "absolute"])
def test_evidence_files_must_stay_inside_bundle(
    tmp_path: Path, invalid_path: str
) -> None:
    bundle = tmp_path / "bundle"
    bundle.mkdir()
    manifest = bundle / "evidence.json"
    outside = tmp_path / "outside.json"
    outside.write_text("outside\n", encoding="utf-8")
    evidence = _evidence(hashlib.sha256(outside.read_bytes()).hexdigest())
    evidence_file = (
        str(outside.resolve()) if invalid_path == "absolute" else invalid_path
    )
    for gate in [*evidence.technical_gates, *evidence.uat_scenarios]:
        gate.evidence_file = evidence_file

    with pytest.raises(ValueError, match=r"相对路径|不在证据包内"):
        verify_evidence_files(evidence, manifest)


def test_evidence_file_hash_mismatch_is_rejected(tmp_path: Path) -> None:
    evidence_file = tmp_path / "gate-evidence.json"
    evidence_file.write_text("actual evidence\n", encoding="utf-8")

    with pytest.raises(ValueError, match="SHA-256 不匹配"):
        verify_evidence_files(_evidence(), tmp_path / "evidence.json")


def test_evidence_file_with_secret_is_rejected(tmp_path: Path) -> None:
    evidence_file = tmp_path / "gate-evidence.json"
    evidence_file.write_text('{"credential":"do-not-store"}\n', encoding="utf-8")
    evidence_hash = hashlib.sha256(evidence_file.read_bytes()).hexdigest()

    with pytest.raises(ValueError, match="疑似 Secret"):
        verify_evidence_files(_evidence(evidence_hash), tmp_path / "evidence.json")


def test_readiness_cli_writes_hashed_non_release_report(tmp_path: Path) -> None:
    authorization_path = tmp_path / "authorization.json"
    preflight_path = tmp_path / "preflight.json"
    visual_path = tmp_path / "visual.json"
    evidence_path = tmp_path / "evidence.json"
    output_path = tmp_path / "readiness.json"
    gate_evidence_path = tmp_path / "gate-evidence.json"
    authorization_path.write_text(
        '{"authorizationStatus":"approved"}\n', encoding="utf-8"
    )
    authorization_hash = hashlib.sha256(authorization_path.read_bytes()).hexdigest()
    gate_evidence_path.write_text('{"synthetic":true}\n', encoding="utf-8")
    gate_evidence_hash = hashlib.sha256(gate_evidence_path.read_bytes()).hexdigest()
    preflight_path.write_text(
        _preflight(authorization_hash).model_dump_json(by_alias=True), encoding="utf-8"
    )
    visual_path.write_text(
        _visual(authorization_hash).model_dump_json(by_alias=True), encoding="utf-8"
    )
    evidence_path.write_text(
        _evidence(gate_evidence_hash, authorization_hash).model_dump_json(),
        encoding="utf-8",
    )

    assert (
        main(
            [
                str(authorization_path),
                str(preflight_path),
                str(visual_path),
                str(evidence_path),
                str(output_path),
            ]
        )
        == 0
    )
    report = json.loads(output_path.read_text(encoding="utf-8"))
    assert report["releaseReady"] is True
    assert report["releaseAuthorized"] is False
    assert report["releaseStatus"] == "NotReleased"
    assert set(report["sourceEvidenceSha256"]) == {
        "authorizationManifest",
        "preflight",
        "visual",
        "evidence",
    }

    authorization_path.write_text(
        '{"authorizationStatus":"changed-after-preflight"}\n', encoding="utf-8"
    )
    mismatch_output_path = tmp_path / "readiness-mismatch.json"
    assert (
        main(
            [
                str(authorization_path),
                str(preflight_path),
                str(visual_path),
                str(evidence_path),
                str(mismatch_output_path),
            ]
        )
        == 2
    )
    mismatch_report = json.loads(
        mismatch_output_path.read_text(encoding="utf-8")
    )
    assert mismatch_report["releaseReady"] is False
    assert mismatch_report["reasons"] == ["authorization_manifest_not_verified"]

    authorization_path.write_text(
        '{"authorizationStatus":"approved"}\n', encoding="utf-8"
    )
    authorization_before_overlap = hashlib.sha256(
        authorization_path.read_bytes()
    ).hexdigest()
    with pytest.raises(SystemExit) as overlap_error:
        main(
            [
                str(authorization_path),
                str(preflight_path),
                str(visual_path),
                str(evidence_path),
                str(authorization_path),
            ]
        )
    assert overlap_error.value.code == 2
    assert hashlib.sha256(authorization_path.read_bytes()).hexdigest() == (
        authorization_before_overlap
    )

    authorization_path.write_text(
        '{"authorizationStatus":"approved","clientSecret":"do-not-store"}\n',
        encoding="utf-8",
    )
    secret_output_path = tmp_path / "readiness-secret.json"
    assert (
        main(
            [
                str(authorization_path),
                str(preflight_path),
                str(visual_path),
                str(evidence_path),
                str(secret_output_path),
            ]
        )
        == 2
    )
    assert not secret_output_path.exists()
