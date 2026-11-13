package com.github.kr328.clash.design

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.view.ViewGroup.LayoutParams
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.github.kr328.clash.design.adapter.ProfileAdapter
import com.github.kr328.clash.design.model.ProfileNodeItem
import com.github.kr328.clash.design.model.ProfileNodeGroupItem
import com.github.kr328.clash.design.model.ProfileNodeSettingsItem
import com.github.kr328.clash.design.databinding.DesignProfilesBinding
import com.github.kr328.clash.design.databinding.DialogProfilesMenuBinding
import com.github.kr328.clash.design.databinding.DialogProfileQrCodeBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

class ProfilesDesign(context: Context) : Design<ProfilesDesign.Request>(context) {
    sealed class Request {
        object UpdateAll : Request()
        object ManageGroups : Request()
        data class TestProfile(val profile: Profile) : Request()
        data class SetDelayThresholds(val good: Int?, val slow: Int?) : Request()
        data class RenameNode(val profile: Profile, val node: ProfileNodeItem, val name: String) : Request()
        data class SetNodeGroup(val profile: Profile, val node: ProfileNodeItem, val groupId: String?) : Request()
        data class CreateNodeGroup(val name: String) : Request()
        data class DeleteNodeGroup(val id: String) : Request()
        data class EnableNodeGroup(val id: String, val enabled: Boolean) : Request()
        object Refresh : Request()
        object Create : Request()
        data class Active(val profile: Profile) : Request()
        data class Update(val profile: Profile) : Request()
        data class Edit(val profile: Profile) : Request()
        data class Duplicate(val profile: Profile) : Request()
        data class ShareQrCode(val profile: Profile) : Request()
        data class Delete(val profile: Profile) : Request()
    }

    private val binding = DesignProfilesBinding
        .inflate(context.layoutInflater, context.root, false)
    private var nodeGroups: List<ProfileNodeGroupItem> = emptyList()
    private var nodeSettings = ProfileNodeSettingsItem()
    private val adapter = ProfileAdapter(
        context, this::requestActive, this::showMenu, this::showNodeDetails,
        { requests.trySend(Request.ManageGroups) },
        { profile -> requests.trySend(Request.TestProfile(profile)) }
    )

    private var allUpdating: Boolean
        get() = adapter.states.allUpdating;
        set(value) {
            adapter.states.allUpdating = value
        }
    private val rotateAnimation : Animation = AnimationUtils.loadAnimation(context, R.anim.rotate_infinite)

    override val root: View
        get() = binding.root

    suspend fun patchProfiles(
        profiles: List<Profile>,
        nodes: Map<java.util.UUID, List<ProfileNodeItem>> = emptyMap(),
        groups: List<ProfileNodeGroupItem> = emptyList(),
        settings: ProfileNodeSettingsItem = ProfileNodeSettingsItem(),
    ) {
        nodeGroups = groups
        nodeSettings = settings
        adapter.setNodes(nodes)
        adapter.apply {
            patchDataSet(this::profiles, profiles, id = { it.uuid })
        }

        val updatable = withContext(Dispatchers.Default) {
            profiles.any { it.imported && it.type != Profile.Type.File }
        }

        withContext(Dispatchers.Main) {
            binding.updateView.visibility = if (updatable) View.VISIBLE else View.GONE
        }
    }

    suspend fun requestSave(profile: Profile) {
        showToast(R.string.active_unsaved_tips, ToastDuration.Long) {
            setAction(R.string.edit) {
                requests.trySend(Request.Edit(profile))
            }
        }
    }

    fun updateElapsed() {
        adapter.updateElapsed()
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.also {
            it.bindAppBarElevation(binding.activityBarLayout)
            it.applyLinearAdapter(context, adapter)
        }
    }

    private fun showMenu(profile: Profile) {
        val dialog = AppBottomSheetDialog(context)

        val binding = DialogProfilesMenuBinding
            .inflate(context.layoutInflater, dialog.window?.decorView as ViewGroup?, false)

        binding.master = this
        binding.self = dialog
        binding.profile = profile

        dialog.setContentView(binding.root)
        dialog.show()
    }

    fun requestUpdateAll() {
        allUpdating = true;
        changeUpdateAllButtonStatus()
        requests.trySend(Request.UpdateAll)
    }

    fun finishUpdateAll() {
        allUpdating = false;
        changeUpdateAllButtonStatus()
    }

    fun requestCreate() {
        requests.trySend(Request.Create)
    }

    private fun requestActive(profile: Profile) {
        requests.trySend(Request.Active(profile))
    }

    fun requestActive(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Active(profile))
        dialog.dismiss()
    }

    fun requestUpdate(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Update(profile))

        dialog.dismiss()
    }

    fun requestEdit(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Edit(profile))

        dialog.dismiss()
    }

    fun requestDuplicate(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Duplicate(profile))

        dialog.dismiss()
    }

    fun requestDelete(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Delete(profile))

        dialog.dismiss()
    }

    fun requestShareQrCode(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.ShareQrCode(profile))

        dialog.dismiss()
    }

    suspend fun showProfileQrCode(profile: Profile) {
        if (profile.type != Profile.Type.Url || profile.source.isBlank()) return

        val bitmap = try {
            withContext(Dispatchers.Default) {
                val matrix = QRCodeWriter().encode(
                    profile.source, BarcodeFormat.QR_CODE, 768, 768,
                    mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
                )
                val pixels = IntArray(matrix.width * matrix.height) { index ->
                    if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
                }
                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            }
        } catch (_: WriterException) {
            showToast(R.string.qr_code_generation_failed, ToastDuration.Long)
            return
        }

        withContext(Dispatchers.Main) {
            val binding = DialogProfileQrCodeBinding.inflate(context.layoutInflater)
            binding.qrCode.setImageBitmap(bitmap)

            suspendCancellableCoroutine<Unit> { continuation ->
                val dialog = MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.share_qr_code)
                    .setView(binding.root)
                    .setPositiveButton(R.string.close) { _, _ -> }
                    .setOnDismissListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    .show()

                continuation.invokeOnCancellation { dialog.dismiss() }
            }
        }
    }

    fun showNodeDetails(profile: Profile, node: ProfileNodeItem) {
        val group = node.groupName ?: "未分组"
        MaterialAlertDialogBuilder(context)
            .setTitle(node.displayName)
            .setMessage("原始名称：${node.originalName}\n协议类型：${node.type}\n所属分组：${group}\n测速状态：${node.delayText}\n节点标识：${node.key}")
            .setPositiveButton("重命名") { _, _ -> showRenameNode(profile, node) }
            .setNegativeButton("分组") { _, _ -> showNodeGroupPicker(profile, node) }
            .setNeutralButton(R.string.close, null)
            .show()
    }

    private fun showRenameNode(profile: Profile, node: ProfileNodeItem) {
        val input = EditText(context).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(node.displayName)
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("重命名节点")
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                requests.trySend(Request.RenameNode(profile, node, input.text.toString().trim()))
            }
            .show()
    }

    private fun showNodeGroupPicker(profile: Profile, node: ProfileNodeItem) {
        val enabled = nodeGroups.filter { it.enabled }
        val labels = listOf("不分组") + enabled.map { it.name }
        MaterialAlertDialogBuilder(context)
            .setTitle("选择节点分组")
            .setItems(labels.toTypedArray()) { _, which ->
                requests.trySend(Request.SetNodeGroup(profile, node, if (which == 0) null else enabled[which - 1].id))
            }
            .setNeutralButton("分组管理") { _, _ -> requests.trySend(Request.ManageGroups) }
            .show()
    }

    fun showNodeGroupManager() {
        val groups = nodeGroups
        val labels = groups.map { if (it.enabled) "✓ ${it.name}" else "○ ${it.name}" }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle("节点分组管理")
            .setItems(labels) { _, which ->
                val group = groups[which]
                MaterialAlertDialogBuilder(context)
                    .setTitle(group.name)
                    .setItems(arrayOf(if (group.enabled) "停用分组" else "启用分组", "删除分组")) { _, action ->
                        if (action == 0) requests.trySend(Request.EnableNodeGroup(group.id, !group.enabled))
                        else requests.trySend(Request.DeleteNodeGroup(group.id))
                    }
                    .show()
            }
            .setPositiveButton("新建分组") { _, _ -> showCreateNodeGroup() }
            .setNeutralButton("测速阈值") { _, _ -> showDelayThresholds() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showDelayThresholds() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 12, 48, 0)
        }
        val good = EditText(context).apply {
            hint = "绿色上限（毫秒）"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(nodeSettings.goodThresholdMs?.toString().orEmpty())
        }
        val slow = EditText(context).apply {
            hint = "黄色上限（毫秒）"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(nodeSettings.slowThresholdMs?.toString().orEmpty())
        }
        container.addView(good, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        container.addView(slow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        MaterialAlertDialogBuilder(context)
            .setTitle("测速延迟阈值")
            .setMessage("留空表示不启用颜色分级；绿色上限必须小于黄色上限。")
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val goodValue = good.text.toString().toIntOrNull()
                val slowValue = slow.text.toString().toIntOrNull()
                if ((goodValue == null) != (slowValue == null) ||
                    (goodValue != null && slowValue != null && (goodValue <= 0 || slowValue <= goodValue))) {
                    launch { showToast("阈值无效，请检查两个毫秒数值。", ToastDuration.Long) }
                } else {
                    requests.trySend(Request.SetDelayThresholds(goodValue, slowValue))
                }
            }
            .show()
    }

    private fun showCreateNodeGroup() {
        val input = EditText(context).apply { setSingleLine(true); hint = "分组名称" }
        MaterialAlertDialogBuilder(context)
            .setTitle("新建节点分组")
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                requests.trySend(Request.CreateNodeGroup(input.text.toString().trim()))
            }
            .show()
    }

    private fun changeUpdateAllButtonStatus() {
        if (allUpdating) {
            binding.updateView.startAnimation(rotateAnimation)
        } else {
            binding.updateView.clearAnimation()
        }
    }
}
