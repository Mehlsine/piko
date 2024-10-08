package crimera.patches.twitter.misc.customize.replySorting

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint


object ReplySortingInvokeClassFinderFingerprint: MethodFingerprint(
    customFingerprint = {it,_->
        it.definingClass == "Lcom/twitter/tweetview/focal/ui/replysorting/ReplySortingViewDelegateBinder;"
    }
)

object ReplySortingLastSelectedFinderFingerprint: MethodFingerprint(
    strings = listOf(
        "controller_data",
        "reply_sorting_enabled",
        "reply_sorting"
    )
)


@Patch(
    name = "Customize reply sort filter",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true
)
@Suppress("unused")
object DefaultReplySortingPatch:BytecodePatch(
    setOf(SettingsStatusLoadFingerprint,ReplySortingInvokeClassFinderFingerprint,ReplySortingLastSelectedFinderFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = ReplySortingInvokeClassFinderFingerprint.result
            ?:throw PatchException("ReplySortingInvokeClassFinderFingerprint not found")

        val replySortingInvokeClass= result.classDef.fields.first().type
        val method = context.findClass(replySortingInvokeClass)!!.mutableClass.directMethods.first()
        val instructions = method.getInstructions()
        val loc = instructions.first{it.opcode == Opcode.SGET_OBJECT}.location.index
        val rClass = (method.getInstruction<ReferenceInstruction>(loc).reference as FieldReference).definingClass
        val r0 = method.getInstruction<OneRegisterInstruction>(loc).registerA
        method.addInstructions(loc+1,
            """
                invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->defaultReplySortFilter()Ljava/lang/String;
                move-result-object v$r0
                invoke-static{v0}, $rClass->valueOf(Ljava/lang/String;)$rClass
                move-result-object v$r0
            """.trimIndent())

        val result2 = ReplySortingLastSelectedFinderFingerprint.result
            ?:throw PatchException("ReplySortingLastSelectedFinderFingerprint not found")

        val method2 = result2.mutableMethod
        result2.scanResult.stringsScanResult!!.matches.forEach{ match ->
            val str = match.string
            if(str.equals("reply_sorting")){
                var loc = match.index
                var r = method2.getInstruction<OneRegisterInstruction>(loc-1).registerA
                method2.addInstructions(loc,"""
                    invoke-static {v$r},${SettingsPatch.PREF_DESCRIPTOR};->setReplySortFilter(Ljava/lang/String;)V
                """.trimIndent())
                return@forEach
            }
        }

        SettingsStatusLoadFingerprint.enableSettings("defaultReplySortFilter")

    }
}