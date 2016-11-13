package com.khartec.waltz.data.complexity;

import com.khartec.waltz.common.Checks;
import com.khartec.waltz.common.MapUtilities;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.complexity.*;
import com.khartec.waltz.schema.tables.records.ComplexityScoreRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.khartec.waltz.common.ListUtilities.ensureNotNull;
import static com.khartec.waltz.schema.tables.ComplexityScore.COMPLEXITY_SCORE;
import static java.util.Optional.ofNullable;

@Repository
public class ComplexityScoreDao {

    private static final Function<Record, ComplexityScore> TO_COMPLEXITY_SCORE_MAPPER = r -> {
        ComplexityScoreRecord record = r.into(COMPLEXITY_SCORE);
        return ImmutableComplexityScore.builder()
                .kind(ComplexityKind.valueOf(record.getComplexityKind()))
                .id(record.getEntityId())
                .score(record.getScore().doubleValue())
                .build();
    };


    private static final BiFunction<Long, Collection<ComplexityScore>, ComplexityRating> TO_COMPLEXITY_RATING =
        (id, scores) -> {
            Map<ComplexityKind, ComplexityScore> scoresByKind = MapUtilities.indexBy(score -> score.kind(), ensureNotNull(scores));
            return ImmutableComplexityRating.builder()
                    .id(id)
                    .serverComplexity(ofNullable(scoresByKind.get(ComplexityKind.SERVER)))
                    .capabilityComplexity(ofNullable(scoresByKind.get(ComplexityKind.CAPABILITY)))
                    .connectionComplexity(ofNullable(scoresByKind.get(ComplexityKind.CONNECTION)))
                    .build();
    };


    private final DSLContext dsl;


    @Autowired
    public ComplexityScoreDao(DSLContext dsl) {
        Checks.checkNotNull(dsl, "dsl cannot be null");
        this.dsl = dsl;
    }


    public ComplexityRating getForApp(long appId) {
        Map<Long, List<ComplexityScore>> scoresForApp = dsl.select(COMPLEXITY_SCORE.fields())
                .from(COMPLEXITY_SCORE)
                .where(COMPLEXITY_SCORE.ENTITY_ID.eq(appId))
                .and(COMPLEXITY_SCORE.ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                .fetch()
                .stream()
                .map(TO_COMPLEXITY_SCORE_MAPPER)
                .collect(Collectors.groupingBy(s -> s.id()));

        return TO_COMPLEXITY_RATING.apply(appId, scoresForApp.get(appId));
    }


    public List<ComplexityRating> findForAppIdSelector(Select<Record1<Long>> appIdSelector) {
        Map<Long, List<ComplexityScore>> scoresForApp = dsl
                .select(COMPLEXITY_SCORE.fields())
                .from(COMPLEXITY_SCORE)
                .where(COMPLEXITY_SCORE.ENTITY_ID.in(appIdSelector))
                .and(COMPLEXITY_SCORE.ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                .fetch()
                .stream()
                .map(TO_COMPLEXITY_SCORE_MAPPER)
                .collect(Collectors.groupingBy(s -> s.id()));

        return scoresForApp
                .entrySet()
                .stream()
                .map(entry -> TO_COMPLEXITY_RATING.apply(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    public int deleteAll() {
        return dsl.deleteFrom(COMPLEXITY_SCORE).execute();
    }

    public int[] bulkInsert(List<ComplexityScoreRecord> records) {
        return dsl.batchInsert(records).execute();
    }
}