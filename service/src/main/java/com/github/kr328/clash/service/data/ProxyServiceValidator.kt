package com.github.kr328.clash.service.data
object ProxyServiceValidator {
 fun validate(services:List<ProxyService>,nodeGroups:List<NodeGroup>,targetSets:List<TargetSet>,nodeMembers:Map<String,List<ProxyServiceNodeGroup>>,bindings:List<ProxyServiceTargetSet>):String? {
  val serviceMap=services.associateBy { it.id }; val groupMap=nodeGroups.associateBy { it.id }; val targetMap=targetSets.associateBy { it.id }
  if(serviceMap.size!=services.size) return "代理服务 ID 重复"
  if(services.map { it.name.trim().lowercase() }.distinct().size!=services.size) return "代理服务名称重复"
  for(s in services.filter { it.enabled }) {
   val members=nodeMembers[s.id].orEmpty()
   if(members.isEmpty()) return "代理服务“${s.name}”没有选择节点组"
   if(members.map { it.nodeGroupId }.distinct().size!=members.size) return "代理服务“${s.name}”重复选择节点组"
   for(m in members) { val g=groupMap[m.nodeGroupId] ?: return "代理服务“${s.name}”引用了不存在的节点组"; if(!g.enabled) return "代理服务“${s.name}”引用了已停用的节点组“${g.name}”" }
  }
  val owners=mutableMapOf<String,String>()
  for(b in bindings) {
   if(b.serviceId !in serviceMap) return "目标集合绑定了不存在的代理服务"
   if(b.targetSetId !in targetMap) return "代理服务绑定了不存在的目标集合"
   if(owners.put(b.targetSetId,b.serviceId)!=null) return "同一个目标集合不能绑定多个代理服务"
  }
  for(t in targetSets.filter { it.enabled }) { val sid=owners[t.id] ?: return "启用的目标集合“${t.name}”尚未绑定代理服务"; val s=serviceMap[sid] ?: return "目标集合绑定的代理服务不存在"; if(!s.enabled) return "目标集合“${t.name}”绑定的代理服务已停用" }
  return null
 }
}