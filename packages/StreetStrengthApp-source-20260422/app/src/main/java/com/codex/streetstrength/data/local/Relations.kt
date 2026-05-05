package com.codex.streetstrength.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class ExerciseTemplateWithVariants(
    @Embedded val template: ExerciseTemplateEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "templateId",
    )
    val variants: List<ExerciseVariantEntity>,
)

data class ExerciseCategoryWithTemplates(
    @Embedded val category: ExerciseCategoryEntity,
    @Relation(
        entity = ExerciseTemplateEntity::class,
        parentColumn = "id",
        entityColumn = "categoryId",
    )
    val templates: List<ExerciseTemplateWithVariants>,
)

data class DayTaskWithDetails(
    @Embedded val task: DayTaskEntity,
    @Relation(
        parentColumn = "templateId",
        entityColumn = "id",
    )
    val template: ExerciseTemplateEntity,
    @Relation(
        parentColumn = "variantId",
        entityColumn = "id",
    )
    val variant: ExerciseVariantEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId",
    )
    val setPlans: List<TaskSetPlanEntity>,
)

data class PlanDayWithTasks(
    @Embedded val day: PlanDayEntity,
    @Relation(
        entity = DayTaskEntity::class,
        parentColumn = "id",
        entityColumn = "dayId",
    )
    val tasks: List<DayTaskWithDetails>,
)

