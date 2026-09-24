package com.ruoyi.aps.application.resource;

import java.util.List;
import java.util.Optional;
import com.ruoyi.aps.application.resource.ResourceCatalog.Availability;
import com.ruoyi.aps.application.resource.ResourceCatalog.Resource;
import com.ruoyi.aps.application.resource.ResourceCatalog.Skill;
import com.ruoyi.aps.application.resource.ResourceCatalog.WorkCenter;
import com.ruoyi.aps.application.resource.ResourceCatalog.Workshop;

/** M01～M05 持久化端口。所有读取必须携带显式数据范围。 */
public interface ResourceRepository
{
    List<Workshop> listWorkshops(ResourceAccessScope scope);

    Optional<Workshop> findWorkshop(ResourceAccessScope scope, String id);

    void insertWorkshop(Workshop value, String actorUserId);

    int updateWorkshop(Workshop value, String actorUserId);

    List<WorkCenter> listWorkCenters(ResourceAccessScope scope, String workshopId);

    Optional<WorkCenter> findWorkCenter(ResourceAccessScope scope, String id);

    void insertWorkCenter(WorkCenter value, String actorUserId);

    int updateWorkCenter(WorkCenter value, String actorUserId);

    List<Resource> listResources(ResourceAccessScope scope, String workshopId, String workCenterId);

    Optional<Resource> findResource(ResourceAccessScope scope, String id);

    void insertResource(Resource value, String actorUserId);

    int updateResource(Resource value, String actorUserId);

    List<Skill> listSkills(ResourceAccessScope scope, String resourceId);

    void insertSkill(Skill value, String actorUserId);

    int updateSkill(Skill value, String actorUserId);

    List<Availability> listAvailability(ResourceAccessScope scope, String resourceId);

    void insertAvailability(Availability value, String actorUserId);

    int updateAvailability(Availability value, String actorUserId);

    boolean hasPlanOrExecutionReference(ResourceAccessScope scope, String resourceId);
}
