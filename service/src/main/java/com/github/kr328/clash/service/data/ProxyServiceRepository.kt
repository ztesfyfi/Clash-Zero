package com.github.kr328.clash.service.data
import androidx.room.withTransaction
class ProxyServiceRepository(private val database:Database=Database.database) {
 suspend fun save(service:ProxyService,nodeGroupIds:List<String>,targetSetIds:List<String>) {
  require(service.name.isNotBlank()) { "代理服务名称不能为空" }
  require(nodeGroupIds.isNotEmpty()) { "代理服务至少选择一个节点组" }
  require(nodeGroupIds.distinct().size==nodeGroupIds.size) { "不能重复选择节点组" }
  require(targetSetIds.distinct().size==targetSetIds.size) { "不能重复绑定目标集合" }
  val groups=database.openNodeGroupDao().queryAll().associateBy { it.id }
  nodeGroupIds.forEach { id -> val g=groups[id] ?: error("所选节点组不存在：$id"); require(g.enabled) { "节点组“${g.name}”已停用" } }
  if (service.mode == ProxyService.MODE_LOAD_BALANCE) {
   val knownRegions = nodeGroupIds.mapNotNull { groups[it]?.regionCode?.uppercase() }.distinct()
   require(knownRegions.size <= 1) { "负载均衡不建议混用多个已标记地区的节点组，请改用手动选择、自动择优或故障转移" }
  }
  val targets=database.openTargetSetDao().queryAll().associateBy { it.id }
  targetSetIds.forEach { id -> val t=targets[id] ?: error("目标集合不存在：$id"); require(t.enabled) { "目标集合“${t.name}”已停用" } }
  val dao=database.openProxyServiceDao()
  require(dao.queryAll().none { it.id!=service.id && it.name.equals(service.name.trim(),true) }) { "代理服务名称已存在" }
  val owners=dao.queryAllTargetBindings().associate { it.targetSetId to it.serviceId }
  targetSetIds.forEach { id -> require(owners[id]==null || owners[id]==service.id) { "目标集合“${targets.getValue(id).name}”已经绑定其他代理服务" } }
  database.withTransaction { dao.replaceConfiguration(service.copy(name=service.name.trim(),updatedAt=System.currentTimeMillis()),nodeGroupIds,targetSetIds) }
 }
 suspend fun delete(id:String) { database.openProxyServiceDao().delete(id) }
}