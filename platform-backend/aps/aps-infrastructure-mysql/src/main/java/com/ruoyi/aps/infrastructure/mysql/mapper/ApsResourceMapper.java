package com.ruoyi.aps.infrastructure.mysql.mapper;

import java.util.List;
import java.util.Map;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.application.resource.ResourceCatalog.Availability;
import com.ruoyi.aps.application.resource.ResourceCatalog.Resource;
import com.ruoyi.aps.application.resource.ResourceCatalog.Skill;
import com.ruoyi.aps.application.resource.ResourceCatalog.WorkCenter;
import com.ruoyi.aps.application.resource.ResourceCatalog.Workshop;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** M01～M05 SQL 映射；所有业务查询在 SQL 内应用车间范围，避免先查后过滤。 */
public interface ApsResourceMapper extends ApsMapper
{
    String WORKSHOP_SCOPE = "(#{scope.allWorkshops} = true OR w.manager_user_id = #{scope.actorUserId})";

    @Select("SELECT w.* FROM aps_workshop w WHERE " + WORKSHOP_SCOPE + " ORDER BY w.workshop_code")
    List<Map<String, Object>> listWorkshops(@Param("scope") ResourceAccessScope scope);

    @Select("SELECT w.* FROM aps_workshop w WHERE w.id=#{id} AND " + WORKSHOP_SCOPE)
    Map<String, Object> findWorkshop(@Param("scope") ResourceAccessScope scope, @Param("id") String id);

    @Insert("INSERT INTO aps_workshop(id,workshop_code,workshop_name,manager_user_id,status,remark,created_by,updated_by) "
            + "VALUES(#{v.id},#{v.code},#{v.name},#{v.managerUserId},#{v.status},#{v.remark},#{actor},#{actor})")
    void insertWorkshop(@Param("v") Workshop value, @Param("actor") String actor);

    @Update("UPDATE aps_workshop SET workshop_code=#{v.code},workshop_name=#{v.name},manager_user_id=#{v.managerUserId},"
            + "status=#{v.status},remark=#{v.remark},updated_by=#{actor},row_version=row_version+1 "
            + "WHERE id=#{v.id} AND row_version=#{v.rowVersion}")
    int updateWorkshop(@Param("v") Workshop value, @Param("actor") String actor);

    @Select("SELECT c.* FROM aps_work_center c JOIN aps_workshop w ON w.id=c.workshop_id "
            + "WHERE c.workshop_id=#{workshopId} AND " + WORKSHOP_SCOPE + " ORDER BY c.center_code")
    List<Map<String, Object>> listWorkCenters(@Param("scope") ResourceAccessScope scope,
            @Param("workshopId") String workshopId);

    @Select("SELECT c.* FROM aps_work_center c JOIN aps_workshop w ON w.id=c.workshop_id "
            + "WHERE c.id=#{id} AND " + WORKSHOP_SCOPE)
    Map<String, Object> findWorkCenter(@Param("scope") ResourceAccessScope scope, @Param("id") String id);

    @Insert("INSERT INTO aps_work_center(id,workshop_id,center_code,center_name,center_type,concurrent_capacity,"
            + "capacity_uom_code,status,remark,created_by,updated_by) VALUES(#{v.id},#{v.workshopId},#{v.code},"
            + "#{v.name},#{v.centerType},#{v.concurrentCapacity},#{v.capacityUomCode},#{v.status},#{v.remark},#{actor},#{actor})")
    void insertWorkCenter(@Param("v") WorkCenter value, @Param("actor") String actor);

    @Update("UPDATE aps_work_center SET center_code=#{v.code},center_name=#{v.name},center_type=#{v.centerType},"
            + "concurrent_capacity=#{v.concurrentCapacity},capacity_uom_code=#{v.capacityUomCode},status=#{v.status},"
            + "remark=#{v.remark},updated_by=#{actor},row_version=row_version+1 WHERE id=#{v.id} AND row_version=#{v.rowVersion}")
    int updateWorkCenter(@Param("v") WorkCenter value, @Param("actor") String actor);

    @Select({"<script>", "SELECT r.* FROM aps_resource r JOIN aps_workshop w ON w.id=r.workshop_id WHERE ",
            WORKSHOP_SCOPE,
            "<if test='workshopId != null'> AND r.workshop_id=#{workshopId}</if>",
            "<if test='workCenterId != null'> AND r.work_center_id=#{workCenterId}</if>",
            " ORDER BY r.resource_code", "</script>"})
    List<Map<String, Object>> listResources(@Param("scope") ResourceAccessScope scope,
            @Param("workshopId") String workshopId, @Param("workCenterId") String workCenterId);

    @Select("SELECT r.* FROM aps_resource r JOIN aps_workshop w ON w.id=r.workshop_id "
            + "WHERE r.id=#{id} AND " + WORKSHOP_SCOPE)
    Map<String, Object> findResource(@Param("scope") ResourceAccessScope scope, @Param("id") String id);

    @Insert("INSERT INTO aps_resource(id,workshop_id,work_center_id,resource_code,resource_name,resource_type,"
            + "ruoyi_user_id,team_name,capacity_value,capacity_uom_code,status,remark,created_by,updated_by) VALUES("
            + "#{v.id},#{v.workshopId},#{v.workCenterId},#{v.code},#{v.name},#{v.type},#{v.ruoyiUserId},#{v.teamName},"
            + "#{v.capacityValue},#{v.capacityUomCode},#{v.status},#{v.remark},#{actor},#{actor})")
    void insertResource(@Param("v") Resource value, @Param("actor") String actor);

    @Update("UPDATE aps_resource SET work_center_id=#{v.workCenterId},resource_code=#{v.code},resource_name=#{v.name},"
            + "resource_type=#{v.type},ruoyi_user_id=#{v.ruoyiUserId},team_name=#{v.teamName},capacity_value=#{v.capacityValue},"
            + "capacity_uom_code=#{v.capacityUomCode},status=#{v.status},remark=#{v.remark},updated_by=#{actor},"
            + "row_version=row_version+1 WHERE id=#{v.id} AND workshop_id=#{v.workshopId} AND row_version=#{v.rowVersion}")
    int updateResource(@Param("v") Resource value, @Param("actor") String actor);

    @Select("SELECT s.* FROM aps_resource_skill s JOIN aps_resource r ON r.id=s.resource_id "
            + "JOIN aps_workshop w ON w.id=r.workshop_id WHERE s.resource_id=#{resourceId} AND " + WORKSHOP_SCOPE
            + " ORDER BY s.skill_code")
    List<Map<String, Object>> listSkills(@Param("scope") ResourceAccessScope scope,
            @Param("resourceId") String resourceId);

    @Insert("INSERT INTO aps_resource_skill(id,resource_id,skill_code,skill_name,skill_level,valid_from,valid_to,"
            + "certificate_ref,status,created_by,updated_by) VALUES(#{v.id},#{v.resourceId},#{v.code},#{v.name},#{v.level},"
            + "#{v.validFrom},#{v.validTo},#{v.certificateRef},#{v.status},#{actor},#{actor})")
    void insertSkill(@Param("v") Skill value, @Param("actor") String actor);

    @Update("UPDATE aps_resource_skill SET skill_code=#{v.code},skill_name=#{v.name},skill_level=#{v.level},"
            + "valid_from=#{v.validFrom},valid_to=#{v.validTo},certificate_ref=#{v.certificateRef},status=#{v.status},"
            + "updated_by=#{actor},row_version=row_version+1 WHERE id=#{v.id} AND resource_id=#{v.resourceId} "
            + "AND row_version=#{v.rowVersion}")
    int updateSkill(@Param("v") Skill value, @Param("actor") String actor);

    @Select("SELECT a.* FROM aps_resource_availability a JOIN aps_resource r ON r.id=a.resource_id "
            + "JOIN aps_workshop w ON w.id=r.workshop_id WHERE a.resource_id=#{resourceId} AND " + WORKSHOP_SCOPE
            + " ORDER BY a.start_at,a.end_at")
    List<Map<String, Object>> listAvailability(@Param("scope") ResourceAccessScope scope,
            @Param("resourceId") String resourceId);

    @Insert("INSERT INTO aps_resource_availability(id,resource_id,window_type,start_at,end_at,capacity_ratio,source_type,"
            + "source_ref,reason,created_by,updated_by) VALUES(#{v.id},#{v.resourceId},#{v.type},#{v.startAt},#{v.endAt},"
            + "#{v.capacityRatio},#{v.sourceType},#{v.sourceRef},#{v.reason},#{actor},#{actor})")
    void insertAvailability(@Param("v") Availability value, @Param("actor") String actor);

    @Update("UPDATE aps_resource_availability SET window_type=#{v.type},start_at=#{v.startAt},end_at=#{v.endAt},"
            + "capacity_ratio=#{v.capacityRatio},source_type=#{v.sourceType},source_ref=#{v.sourceRef},reason=#{v.reason},"
            + "updated_by=#{actor},row_version=row_version+1 WHERE id=#{v.id} AND resource_id=#{v.resourceId} "
            + "AND row_version=#{v.rowVersion}")
    int updateAvailability(@Param("v") Availability value, @Param("actor") String actor);

    @Select("SELECT (EXISTS(SELECT 1 FROM aps_plan_allocation p WHERE p.resource_id=#{resourceId}) "
            + "OR EXISTS(SELECT 1 FROM aps_plan_lock l WHERE l.locked_resource_id=#{resourceId}) "
            + "OR EXISTS(SELECT 1 FROM aps_actual_occupancy a WHERE a.resource_id=#{resourceId})) "
            + "FROM aps_resource r JOIN aps_workshop w ON w.id=r.workshop_id "
            + "WHERE r.id=#{resourceId} AND " + WORKSHOP_SCOPE)
    Boolean hasPlanOrExecutionReference(@Param("scope") ResourceAccessScope scope,
            @Param("resourceId") String resourceId);
}
