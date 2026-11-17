package com.github.kr328.clash.service.data
import androidx.room.*
@Dao interface ProxyServiceDao {
 @Query("SELECT * FROM proxy_services ORDER BY sortOrder, createdAt, name") suspend fun queryAll():List<ProxyService>
 @Query("SELECT * FROM proxy_services WHERE id=:id") suspend fun queryById(id:String):ProxyService?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsert(service:ProxyService)
 @Query("DELETE FROM proxy_services WHERE id=:id") suspend fun delete(id:String)
 @Query("SELECT * FROM proxy_service_node_groups WHERE serviceId=:serviceId ORDER BY position") suspend fun queryNodeGroups(serviceId:String):List<ProxyServiceNodeGroup>
 @Query("SELECT * FROM proxy_service_node_groups WHERE nodeGroupId=:nodeGroupId") suspend fun queryServicesForNodeGroup(nodeGroupId:String):List<ProxyServiceNodeGroup>
 @Query("SELECT * FROM proxy_service_target_sets WHERE serviceId=:serviceId") suspend fun queryTargetSets(serviceId:String):List<ProxyServiceTargetSet>
 @Query("SELECT * FROM proxy_service_target_sets") suspend fun queryAllTargetBindings():List<ProxyServiceTargetSet>
 @Query("DELETE FROM proxy_service_node_groups WHERE serviceId=:serviceId") suspend fun clearNodeGroups(serviceId:String)
 @Query("DELETE FROM proxy_service_target_sets WHERE serviceId=:serviceId") suspend fun clearTargetSets(serviceId:String)
 @Transaction suspend fun replaceConfiguration(service:ProxyService,nodeGroupIds:List<String>,targetSetIds:List<String>) {
  upsert(service); clearNodeGroups(service.id); clearTargetSets(service.id)
  nodeGroupIds.distinct().forEachIndexed { i,id -> addNodeGroup(ProxyServiceNodeGroup(service.id,id,i)) }
  targetSetIds.distinct().forEach { id -> addTargetSet(ProxyServiceTargetSet(id,service.id)) }
 }
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun addNodeGroup(member:ProxyServiceNodeGroup)
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun addTargetSet(binding:ProxyServiceTargetSet)
}