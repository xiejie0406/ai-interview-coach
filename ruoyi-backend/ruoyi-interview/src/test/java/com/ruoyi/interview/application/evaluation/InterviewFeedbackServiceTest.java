package com.ruoyi.interview.application.evaluation;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.domain.catalog.*;
import com.ruoyi.interview.domain.interview.*;
import com.ruoyi.interview.domain.platform.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class InterviewFeedbackServiceTest {
    private final TenantId tenant=TenantId.of("test");
    private final ResourceId q=ResourceId.of("q"), r=ResourceId.of("r"), t=ResourceId.of("t");
    private final RubricVersion rubric=new RubricVersion(r,tenant,q,1,"hash",List.of(
            new RubricDimension("scope","适用范围",true,List.of("明确适用边界"))),"证据不足不下结论",Instant.EPOCH);
    private InterviewTurn turn() {
        return InterviewTurn.rehydrate(t,1,new PlannedQuestion(1,new ImmutableVersionRef(q,1,"qh"),
            rubric.versionRef(),"java",new TimeBudget(Duration.ofMinutes(1)),0),null,TurnKind.PRIMARY,TurnState.ANSWER_CONFIRMED,
            new QuestionPrompt("虚拟线程适合什么？","ph",Optional.empty(),Optional.empty()),
            new InterviewAnswerVersion(ResourceId.of("a"),tenant,ResourceId.of("s"),t,1,InterviewAnswerSource.TEXT,
                "虚拟线程适合阻塞网络请求。","ah",Optional.empty(),UserId.of("1"),Instant.EPOCH,Optional.empty()),Instant.EPOCH);
    }
    private Map<String,Object> output(String quote,String judgement,String code) {
        return Map.of("turns",List.of(Map.of("turnId","t","dimensions",List.of(Map.of("code",code,
            "judgement",judgement,"quote",quote,"feedback","补充测量结果")))));
    }
    @Test void rejectsInventedQuotesUnknownCriteriaAndNumericScores() {
        for(var bad:List.of(output("计算任务更快","SUPPORTED","scope"),output("","SUPPORTED","scope"),
                output("阻塞网络请求","95","scope"),output("阻塞网络请求","PARTIAL","invented")))
            assertThrows(ApplicationException.class,() -> InterviewFeedbackService.validate(bad,List.of(turn()),Map.of("t",rubric)));
    }
    @Test void bindsReportToConfirmedAnswerAndServerRubric() {
        var report=InterviewFeedbackService.validate(output("阻塞网络请求","PARTIAL","scope"),List.of(turn()),Map.of("t",rubric));
        assertEquals("a",report.getFirst().answerVersionId());
        assertEquals("r",report.getFirst().rubricVersionId());
        assertTrue(report.getFirst().dimensions().getFirst().criterion().contains("明确适用边界"));
    }
    @Test void rejectsMissingOrDuplicateTurns() {
        assertThrows(ApplicationException.class,() -> InterviewFeedbackService.validate(Map.of("turns",List.of()),List.of(turn()),Map.of("t",rubric)));
        var rows=(List<?>)output("阻塞网络请求","PARTIAL","scope").get("turns");
        assertThrows(ApplicationException.class,() -> InterviewFeedbackService.validate(Map.of("turns",List.of(rows.getFirst(),rows.getFirst())),List.of(turn(),turn()),Map.of("t",rubric)));
    }
}
