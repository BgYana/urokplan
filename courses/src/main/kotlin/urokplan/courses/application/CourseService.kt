package urokplan.courses.application

import urokplan.courses.domain.Course
import urokplan.courses.domain.CourseFormat
import urokplan.courses.domain.Enrollment
import urokplan.courses.domain.GroupMembership
import urokplan.courses.domain.ParticipationStatus
import urokplan.courses.domain.StudyGroup
import urokplan.database.TransactionRunner
import urokplan.identity.application.AccountReference
import urokplan.identity.application.IdentityApi
import urokplan.identity.domain.AccountRole
import urokplan.relationships.application.RelationshipsApi
import java.net.URI
import java.time.Clock
import java.util.UUID

class CourseService(
    private val transactions: TransactionRunner,
    private val repository: CourseRepository,
    private val identity: IdentityApi,
    private val relationships: RelationshipsApi,
    private val groupSchedules: GroupScheduleUpdater,
    private val clock: Clock,
) : CourseUseCases, CoursesApi {
    override fun createCourse(actorId: UUID, command: CreateCourseCommand): CourseView {
        val teacher = requireAccount(actorId)
        requireRole(teacher, AccountRole.TEACHER, "actor_is_not_teacher")
        val now = clock.instant()
        val course = Course(
            id = UUID.randomUUID(),
            teacherAccountId = actorId,
            title = validateText(command.title, "invalid_title"),
            subject = validateText(command.subject, "invalid_subject"),
            format = command.format,
            callLink = validateCallLink(command.callLink),
            createdAt = now,
            updatedAt = now,
        )
        return transactions.inTransaction { repository.createCourse(course) }.toView(teacher)
    }

    override fun listCourses(actorId: UUID): List<CourseView> {
        val childIds = relationships.findActiveGuardianStudentIds(actorId)
        val courses = transactions.inTransaction { repository.listAccessibleCourses(actorId, childIds) }
        val teachers = identity.findAccountsByIds(courses.mapTo(mutableSetOf()) { it.teacherAccountId })
        return courses.map { it.toView(teachers.getValue(it.teacherAccountId)) }
    }

    override fun getCourse(actorId: UUID, courseId: UUID): CourseView {
        val childIds = relationships.findActiveGuardianStudentIds(actorId)
        val course = transactions.inTransaction {
            repository.findCourse(courseId)?.takeIf { canAccess(it, actorId, childIds) }
        } ?: notFound("course_not_found")
        return course.toView(requireAccount(course.teacherAccountId))
    }

    override fun updateCourse(actorId: UUID, courseId: UUID, command: UpdateCourseCommand): CourseView {
        val updated = transactions.inTransaction {
            val course = requireOwnedCourse(actorId, courseId)
            repository.updateCourse(
                course.copy(
                    title = validateText(command.title, "invalid_title"),
                    subject = validateText(command.subject, "invalid_subject"),
                    callLink = validateCallLink(command.callLink),
                    updatedAt = clock.instant(),
                ),
            )
        }
        return updated.toView(requireAccount(updated.teacherAccountId))
    }

    override fun addEnrollment(actorId: UUID, courseId: UUID, command: AddEnrollmentCommand): EnrollmentView {
        requirePrice(command.priceMinor)
        val student = requireTarget(command.studentPublicId)
        requireRole(student, AccountRole.STUDENT, "target_is_not_student")
        transactions.inTransaction { requireOwnedCourse(actorId, courseId) }
        if (!relationships.hasActiveLearningConnection(actorId, student.id)) {
            forbidden("active_learning_connection_required")
        }
        val enrollment = transactions.inTransaction {
            val course = requireOwnedCourseForUpdate(actorId, courseId)
            val existing = repository.findEnrollment(courseId, student.id)
            if (existing?.status == ParticipationStatus.ACTIVE) conflict("enrollment_already_active")
            if (course.format == CourseFormat.INDIVIDUAL &&
                repository.listEnrollments(courseId).any {
                    it.status == ParticipationStatus.ACTIVE && it.studentAccountId != student.id
                }
            ) {
                conflict("individual_course_already_has_student")
            }
            val now = clock.instant()
            repository.saveEnrollment(
                existing?.copy(
                    priceMinor = command.priceMinor,
                    status = ParticipationStatus.ACTIVE,
                    endedAt = null,
                ) ?: Enrollment(courseId, student.id, command.priceMinor, ParticipationStatus.ACTIVE, now),
            )
        }
        return enrollment.toView(student)
    }

    override fun listEnrollments(actorId: UUID, courseId: UUID): List<EnrollmentView> {
        val childIds = relationships.findActiveGuardianStudentIds(actorId)
        val enrollments = transactions.inTransaction {
            val course = repository.findCourse(courseId) ?: notFound("course_not_found")
            when {
                course.teacherAccountId == actorId -> repository.listEnrollments(courseId)
                else -> repository.listEnrollments(courseId)
                    .filter {
                        it.status == ParticipationStatus.ACTIVE &&
                            (it.studentAccountId == actorId || it.studentAccountId in childIds)
                    }
                    .takeIf(List<Enrollment>::isNotEmpty)
                    ?: notFound("course_not_found")
            }
        }
        val students = identity.findAccountsByIds(enrollments.mapTo(mutableSetOf()) { it.studentAccountId })
        return enrollments.map { it.toView(students.getValue(it.studentAccountId)) }
    }

    override fun updateEnrollmentPrice(
        actorId: UUID,
        courseId: UUID,
        studentPublicId: String,
        priceMinor: Long,
    ): EnrollmentView {
        requirePrice(priceMinor)
        val student = requireTarget(studentPublicId)
        val enrollment = transactions.inTransaction {
            requireOwnedCourse(actorId, courseId)
            repository.findEnrollment(courseId, student.id)
                ?.takeIf { it.status == ParticipationStatus.ACTIVE }
                ?: notFound("enrollment_not_found")
            repository.updateEnrollmentPrice(courseId, student.id, priceMinor)
        }
        return enrollment.toView(student)
    }

    override fun updateAllEnrollmentPrices(actorId: UUID, courseId: UUID, priceMinor: Long): List<EnrollmentView> {
        requirePrice(priceMinor)
        val enrollments = transactions.inTransaction {
            requireOwnedCourse(actorId, courseId)
            repository.updateActiveEnrollmentPrices(courseId, priceMinor)
            repository.listEnrollments(courseId).filter { it.status == ParticipationStatus.ACTIVE }
        }
        val students = identity.findAccountsByIds(enrollments.mapTo(mutableSetOf()) { it.studentAccountId })
        return enrollments.map { it.toView(students.getValue(it.studentAccountId)) }
    }

    override fun removeEnrollment(actorId: UUID, courseId: UUID, studentPublicId: String) {
        val student = requireTarget(studentPublicId)
        transactions.inTransaction {
            requireOwnedCourse(actorId, courseId)
            repository.findEnrollment(courseId, student.id)
                ?.takeIf { it.status == ParticipationStatus.ACTIVE }
                ?: notFound("enrollment_not_found")
            val now = clock.instant()
            groupSchedules.removeStudentFromCourseFutureGroupLessons(courseId, student.id)
            repository.deactivateMemberships(courseId, student.id, now)
            repository.deactivateEnrollment(courseId, student.id, now)
        }
    }

    override fun createGroup(actorId: UUID, command: CreateGroupCommand): GroupView {
        val now = clock.instant()
        val group = transactions.inTransaction {
            val course = requireOwnedCourse(actorId, command.courseId)
            if (course.format != CourseFormat.GROUP) badRequest("group_course_required")
            repository.createGroup(
                StudyGroup(
                    id = UUID.randomUUID(),
                    courseId = course.id,
                    name = validateText(command.name, "invalid_group_name"),
                    callLink = validateCallLink(command.callLink),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        return group.toView()
    }

    override fun listGroups(actorId: UUID): List<GroupView> {
        val childIds = relationships.findActiveGuardianStudentIds(actorId)
        return transactions.inTransaction { repository.listAccessibleGroups(actorId, childIds) }.map { it.toView() }
    }

    override fun getGroup(actorId: UUID, groupId: UUID): GroupView {
        val childIds = relationships.findActiveGuardianStudentIds(actorId)
        val group = transactions.inTransaction {
            repository.findGroup(groupId)?.takeIf {
                val course = repository.findCourse(it.courseId) ?: return@takeIf false
                canAccess(course, actorId, childIds) &&
                    (course.teacherAccountId == actorId ||
                        repository.findActiveMembership(course.id, actorId)?.groupId == groupId ||
                        childIds.any { childId -> repository.findActiveMembership(course.id, childId)?.groupId == groupId })
            }
        } ?: notFound("group_not_found")
        return group.toView()
    }

    override fun updateGroup(actorId: UUID, groupId: UUID, command: UpdateGroupCommand): GroupView {
        val group = transactions.inTransaction {
            val current = requireOwnedGroup(actorId, groupId)
            repository.updateGroup(
                current.copy(
                    name = validateText(command.name, "invalid_group_name"),
                    callLink = validateCallLink(command.callLink),
                    updatedAt = clock.instant(),
                ),
            )
        }
        return group.toView()
    }

    override fun addGroupMember(actorId: UUID, groupId: UUID, studentPublicId: String): GroupMemberView {
        val student = requireTarget(studentPublicId)
        val membership = try {
            transactions.inTransaction {
                val group = requireOwnedGroup(actorId, groupId)
                val enrollment = repository.findEnrollment(group.courseId, student.id)
                    ?.takeIf { it.status == ParticipationStatus.ACTIVE }
                    ?: badRequest("active_enrollment_required")
                if (repository.findActiveMembership(group.courseId, student.id) != null) {
                    conflict("student_already_in_group")
                }
                val created = repository.createMembership(
                    GroupMembership(
                        id = UUID.randomUUID(),
                        groupId = group.id,
                        courseId = group.courseId,
                        studentAccountId = student.id,
                        status = ParticipationStatus.ACTIVE,
                        joinedAt = clock.instant(),
                    ),
                )
                groupSchedules.addStudentToFutureLessons(group.id, student.id, enrollment.priceMinor)
                created
            }
        } catch (_: DuplicateActiveMembershipException) {
            conflict("student_already_in_group")
        } catch (_: GroupScheduleConflictException) {
            conflict("schedule_conflict")
        }
        return membership.toView(student)
    }

    override fun listGroupMembers(actorId: UUID, groupId: UUID): List<GroupMemberView> {
        val memberships = transactions.inTransaction {
            requireOwnedGroup(actorId, groupId)
            repository.listActiveMemberships(groupId)
        }
        val students = identity.findAccountsByIds(memberships.mapTo(mutableSetOf()) { it.studentAccountId })
        return memberships.map { it.toView(students.getValue(it.studentAccountId)) }
    }

    override fun removeGroupMember(actorId: UUID, groupId: UUID, studentPublicId: String) {
        val student = requireTarget(studentPublicId)
        transactions.inTransaction {
            val group = requireOwnedGroup(actorId, groupId)
            val membership = repository.findActiveMembership(group.courseId, student.id)
                ?.takeIf { it.groupId == group.id }
                ?: notFound("group_member_not_found")
            groupSchedules.removeStudentFromFutureLessons(group.id, student.id)
            repository.deactivateMembership(membership.id, clock.instant())
        }
    }

    override fun findCourse(id: UUID): CourseReference? = transactions.inTransaction {
        repository.findCourse(id)?.let {
            CourseReference(it.id, it.teacherAccountId, it.format, it.title, it.callLink)
        }
    }

    override fun findActiveEnrollment(courseId: UUID, studentId: UUID): EnrollmentReference? =
        transactions.inTransaction {
            repository.findEnrollment(courseId, studentId)
                ?.takeIf { it.status == ParticipationStatus.ACTIVE }
                ?.let { EnrollmentReference(it.courseId, it.studentAccountId, it.priceMinor) }
        }

    override fun findEnrollment(courseId: UUID, studentId: UUID): EnrollmentReference? =
        transactions.inTransaction {
            repository.findEnrollment(courseId, studentId)
                ?.let { EnrollmentReference(it.courseId, it.studentAccountId, it.priceMinor) }
        }

    override fun findActiveEnrollments(courseId: UUID): List<EnrollmentReference> =
        transactions.inTransaction {
            repository.listEnrollments(courseId)
                .filter { it.status == ParticipationStatus.ACTIVE }
                .map { EnrollmentReference(it.courseId, it.studentAccountId, it.priceMinor) }
        }

    override fun findGroup(id: UUID): GroupReference? = transactions.inTransaction {
        repository.findGroup(id)?.let { group ->
            GroupReference(
                id = group.id,
                courseId = group.courseId,
                callLink = group.callLink,
                studentIds = repository.listActiveMemberships(group.id)
                    .mapTo(mutableSetOf()) { it.studentAccountId },
            )
        }
    }

    private fun requireOwnedCourse(actorId: UUID, courseId: UUID): Course {
        val course = repository.findCourse(courseId) ?: notFound("course_not_found")
        if (course.teacherAccountId != actorId) forbidden("course_owner_required")
        return course
    }

    private fun requireOwnedCourseForUpdate(actorId: UUID, courseId: UUID): Course {
        val course = repository.findCourseForUpdate(courseId) ?: notFound("course_not_found")
        if (course.teacherAccountId != actorId) forbidden("course_owner_required")
        return course
    }

    private fun requireOwnedGroup(actorId: UUID, groupId: UUID): StudyGroup {
        val group = repository.findGroup(groupId) ?: notFound("group_not_found")
        requireOwnedCourse(actorId, group.courseId)
        return group
    }

    private fun canAccess(course: Course, actorId: UUID, childIds: Set<UUID>): Boolean =
        course.teacherAccountId == actorId ||
            repository.findEnrollment(course.id, actorId)?.status == ParticipationStatus.ACTIVE ||
            childIds.any { repository.findEnrollment(course.id, it)?.status == ParticipationStatus.ACTIVE }

    private fun requireAccount(id: UUID): AccountReference =
        identity.findAccountsByIds(setOf(id))[id] ?: notFound("account_not_found")

    private fun requireTarget(publicId: String): AccountReference =
        identity.findAccountByPublicId(publicId) ?: notFound("account_not_found")

    private fun requireRole(account: AccountReference, role: AccountRole, code: String) {
        if (role !in account.roles) badRequest(code)
    }

    private fun validateText(value: String, code: String): String {
        val normalized = value.trim()
        if (normalized.length !in 2..100 || normalized.any(Char::isISOControl)) badRequest(code)
        return normalized
    }

    private fun validateCallLink(value: String?): String? {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val uri = runCatching { URI(normalized) }.getOrNull()
        if (normalized.length > 500 || uri?.scheme !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
            badRequest("invalid_call_link")
        }
        return normalized
    }

    private fun requirePrice(priceMinor: Long) {
        if (priceMinor <= 0) badRequest("invalid_price")
    }

    private fun Course.toView(teacher: AccountReference) = CourseView(
        id, teacher, title, subject, format, callLink, createdAt, updatedAt,
    )

    private fun Enrollment.toView(student: AccountReference) = EnrollmentView(
        courseId, student, priceMinor, status, enrolledAt, endedAt,
    )

    private fun StudyGroup.toView() = GroupView(id, courseId, name, callLink, createdAt, updatedAt)

    private fun GroupMembership.toView(student: AccountReference) =
        GroupMemberView(id, student, joinedAt)

    private fun badRequest(code: String): Nothing = throw CourseException(code, CourseErrorKind.BAD_REQUEST)
    private fun forbidden(code: String): Nothing = throw CourseException(code, CourseErrorKind.FORBIDDEN)
    private fun notFound(code: String): Nothing = throw CourseException(code, CourseErrorKind.NOT_FOUND)
    private fun conflict(code: String): Nothing = throw CourseException(code, CourseErrorKind.CONFLICT)
}
