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

/**
 * 표정. 눈·입은 필수, 눈썹은 있는 것도 있고 없는 것도 있다(null).
 * 눈과 눈썹 이미지는 "한 짝"짜리라 좌우로 두 번 그린다.
 */
enum class AvatarFace(
    val id: String,
    val label: String,
    val eyeAsset: String,
    val mouthAsset: String,
    val browAsset: String? = null
) {
    BASIC("basic", "방긋", "facial_part_eye_open", "facial_part_mouth_happy"),
    SMIRK("smirk", "새침", "facial_part_eye_half_top", "facial_part_mouth_smirk"),
    SLEEPY("sleepy", "졸린", "facial_part_eye_closed_down", "facial_part_mouth_smirk"),
    GRIN("grin", "헤벌쭉", "facial_part_eye_closed_up", "facial_part_mouth_happy"),
    SULKY("sulky", "뿌루퉁", "facial_part_eye_half_bottom", "facial_part_mouth_sad", "facial_part_eyebrow_a"),
    FIERCE("fierce", "씩씩", "facial_part_eye_open", "facial_part_mouth_angry", "facial_part_eyebrow_c"),
    WINK("wink", "윙크", "facial_part_eye_half_top_wing", "facial_part_mouth_smirk"),
    PROUD("proud", "으쓱", "facial_part_eye_half_top", "facial_part_mouth_smirk", "facial_part_eyebrow_d");

    companion object {
        /** 일반 등급에 쓰는 순한 표정들 */
        val FRIENDLY = listOf(BASIC, SMIRK, SLEEPY, GRIN)
        /** 희귀 이상에 쓰는 개성 있는 표정들 */
        val CHARACTERFUL = listOf(SULKY, FIERCE, WINK, PROUD)
    }
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
        // 일반 등급 24종 — 순한 표정 4종을 돌려가며 배정해 같은 얼굴이 몰리지 않게 한다
        var i = 0
        for (shape in AvatarShape.entries) {
            for (color in AvatarColor.entries) {
                // +1 오프셋: 기본 아바타(square_blue)가 BASIC 표정을 유지하도록 맞춘 값.
                // 이걸 바꾸면 DEFAULT_ID 가 가리키는 아이디가 사라진다.
                val face = AvatarFace.FRIENDLY[(i + 1) % AvatarFace.FRIENDLY.size]
                add(make(shape, color, face, AvatarGrade.COMMON, Unlock.Coin(shape.price)))
                i++
            }
        }
        // 희귀 12 + 영웅 8 + 전설 4 — 개성 있는 표정 4종을 돌려가며 배정
        var index = 0
        for (shape in AvatarShape.entries) {
            for (color in AvatarColor.entries) {
                val (grade, unlock) = when {
                    index < 12 -> AvatarGrade.RARE to Unlock.Level(RARE_LEVELS[index])
                    index < 20 -> AvatarGrade.EPIC to Unlock.BossClear(EPIC_BOSS_STAGES[index - 12])
                    else -> AvatarGrade.LEGENDARY to Unlock.Achieve(LEGENDARY_ACHIEVEMENTS[index - 20])
                }
                val face = AvatarFace.CHARACTERFUL[index % AvatarFace.CHARACTERFUL.size]
                add(make(shape, color, face, grade, unlock))
                index++
            }
        }
    }

    private val byId: Map<String, AvatarDef> = ALL.associateBy { it.id }

    fun byId(id: String): AvatarDef? = byId[id]

    /** 저장된 아이디가 낡아서 사라졌을 때도 빈 아바타가 뜨지 않게 한다 */
    fun byIdOrDefault(id: String): AvatarDef =
        byId[id] ?: byId[DEFAULT_ID] ?: ALL.first()

    fun unlockDescription(def: AvatarDef): String = when (val u = def.unlock) {
        is Unlock.Coin -> "${u.price}코인"
        is Unlock.Level -> "레벨 ${u.level} 달성"
        is Unlock.BossClear -> "${u.stage}스테이지 보스 클리어"
        is Unlock.Achieve -> Achievements.labelOf(u.achievementId)
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
