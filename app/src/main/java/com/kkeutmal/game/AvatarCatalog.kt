package com.kkeutmal.game

enum class AvatarShape(val id: String, val label: String, val price: Int) {
    SQUARE("square", "네모", 150),
    CIRCLE("circle", "동글이", 200),
    SQUIRCLE("squircle", "모난동글", 250),
    RHOMBUS("rhombus", "마름모", 300)
}

enum class AvatarColor(val id: String, val label: String) {
    RED("red", "빨강"),
    PURPLE("purple", "보라"),
    GREEN("green", "초록"),
    BLUE("blue", "파랑"),
    PINK("pink", "분홍"),
    YELLOW("yellow", "노랑")
}

enum class AvatarFace(val id: String, val label: String, val eyeAsset: String, val mouthAsset: String) {
    BASIC("basic", "방긋", "facial_part_eye_open", "facial_part_mouth_happy"),
    SPECIAL("special", "새침", "facial_part_eye_half_top", "facial_part_mouth_smirk")
}

enum class AvatarGrade(val label: String) {
    COMMON("일반"), RARE("희귀"), EPIC("영웅"), LEGENDARY("전설")
}

sealed class Unlock {
    data class Coin(val price: Int) : Unlock()
    data class Level(val level: Int) : Unlock()
    data class BossClear(val stage: Int) : Unlock()
    data class Achieve(val achievementId: String) : Unlock()
}

data class AvatarDef(
    val id: String,
    val name: String,
    val shape: AvatarShape,
    val color: AvatarColor,
    val face: AvatarFace,
    val grade: AvatarGrade,
    val unlock: Unlock
) {
    val bodyAsset: String get() = "${color.id}_body_${shape.id}"
}

object AvatarCatalog {
    const val DEFAULT_ID = "square_blue_basic"

    private val RARE_LEVELS = listOf(5, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70, 80)
    private val EPIC_BOSS_STAGES = listOf(5, 10, 15, 20, 25, 30, 35, 40)
    private val LEGENDARY_ACHIEVEMENTS = listOf("ach_rounds_20", "ach_stage_50", "ach_collect_30", "ach_streak_7")

    val ALL: List<AvatarDef> = buildList {
        // 기본 표정 24종 = 일반 등급
        for (shape in AvatarShape.entries) {
            for (color in AvatarColor.entries) {
                add(make(shape, color, AvatarFace.BASIC, AvatarGrade.COMMON, Unlock.Coin(shape.price)))
            }
        }
        // 특수 표정 24종 = 희귀 12 + 영웅 8 + 전설 4
        var index = 0
        for (shape in AvatarShape.entries) {
            for (color in AvatarColor.entries) {
                val (grade, unlock) = when {
                    index < 12 -> AvatarGrade.RARE to Unlock.Level(RARE_LEVELS[index])
                    index < 20 -> AvatarGrade.EPIC to Unlock.BossClear(EPIC_BOSS_STAGES[index - 12])
                    else -> AvatarGrade.LEGENDARY to Unlock.Achieve(LEGENDARY_ACHIEVEMENTS[index - 20])
                }
                add(make(shape, color, AvatarFace.SPECIAL, grade, unlock))
                index++
            }
        }
    }

    private val byId: Map<String, AvatarDef> = ALL.associateBy { it.id }

    fun byId(id: String): AvatarDef? = byId[id]

    fun unlockDescription(def: AvatarDef): String = when (val u = def.unlock) {
        is Unlock.Coin -> "${u.price}코인"
        is Unlock.Level -> "레벨 ${u.level} 달성"
        is Unlock.BossClear -> "${u.stage}스테이지 보스 클리어"
        // TODO(Task 5): Achievements.labelOf(u.achievementId) 로 교체
        is Unlock.Achieve -> u.achievementId
    }

    private fun make(
        shape: AvatarShape,
        color: AvatarColor,
        face: AvatarFace,
        grade: AvatarGrade,
        unlock: Unlock
    ) = AvatarDef(
        id = "${shape.id}_${color.id}_${face.id}",
        name = "${face.label} ${color.label} ${shape.label}",
        shape = shape,
        color = color,
        face = face,
        grade = grade,
        unlock = unlock
    )
}
