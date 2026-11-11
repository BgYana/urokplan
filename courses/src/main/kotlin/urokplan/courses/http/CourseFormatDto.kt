package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
enum class CourseFormatDto {
    INDIVIDUAL,
    GROUP,
}
