package eu.darken.capod.troubleshooter.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.navigation.popBackStack
import eu.darken.capod.common.uix.Fragment3
import eu.darken.capod.common.viewbinding.viewBinding
import eu.darken.capod.databinding.TroubleshooterFragmentBinding
import eu.darken.capod.troubleshooter.ui.TroubleShooterFragmentVM.BleState
import javax.inject.Inject


@AndroidEntryPoint
class TroubleShooterFragment : Fragment3(R.layout.troubleshooter_fragment) {

    override val vm: TroubleShooterFragmentVM by viewModels()
    override val ui: TroubleshooterFragmentBinding by viewBinding()

    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
        }

        ui.bleIntroStartAction.setOnClickListener { vm.troubleShootBle() }

        vm.bleState.observe2(ui) { state ->
            bleIntroContainer.isVisible = state is BleState.Intro
            bleProcessContainer.isVisible = state is BleState.Working
            bleResultContainer.isVisible = state is BleState.Result

            when (state) {
                is BleState.Intro -> {}
                is BleState.Working -> {
                    bleProcessHistory.apply {
                        text = ""
                        state.allSteps.forEachIndexed { index, s -> append("#$index: $s\n") }
                    }
                }
                is BleState.Result -> {
                    bleResultHistory.apply {
                        text = ""
                        state.history.forEachIndexed { index, s -> append("#$index: $s\n") }
                    }


                    when (state) {
                        is BleState.Result.Success -> {
                            bleResultAction.isVisible = false
                            bleResultTitle.text = getString(R.string.troubleshooter_ble_result_success_title)
                            bleResultBody.text = getString(R.string.troubleshooter_ble_result_success_body)
                            bleResultAction.text = getString(R.string.general_close_action)
                            bleResultAction.setOnClickListener { popBackStack() }
                        }
                        is BleState.Result.Failure -> {
                            bleResultTitle.text = getString(R.string.troubleshooter_ble_result_failure_title)
                            bleResultBody.text = when (state.failureType) {
                                BleState.Result.Failure.Type.PHONE -> getString(R.string.troubleshooter_ble_result_failure_phone_body)
                                BleState.Result.Failure.Type.HEADPHONES -> getString(R.string.troubleshooter_ble_result_failure_phone_headphones)
                            }
                            bleResultAction.text = getString(R.string.general_check_action)
                            bleResultAction.setOnClickListener { vm.troubleShootBle() }
                        }
                    }
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
