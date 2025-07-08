package com.example.tareamov.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tareamov.R
import com.example.tareamov.databinding.FragmentNotificacionesBinding

class NotificacionesFragment : Fragment() {

    private var _binding: FragmentNotificacionesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificacionesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNavigation()
        setupTabs()
    }

    private fun setupNavigation() {
        binding.homeButton.setOnClickListener {
            findNavController().navigate(R.id.action_notificacionesFragment_to_videoHomeFragment)
        }

        binding.exploreButton.setOnClickListener {
            findNavController().navigate(R.id.action_notificacionesFragment_to_exploreFragment)
        }

        binding.goToHomeButton.setOnClickListener {
            findNavController().navigate(R.id.action_notificacionesFragment_to_contentUploadFragment)
        }

        binding.activityButton.setOnClickListener {
            // Ya estamos en la actividad, no hacemos nada
        }

        binding.profileNavButton.setOnClickListener {
            findNavController().navigate(R.id.action_notificacionesFragment_to_profileFragment)
        }
    }

    private fun setupTabs() {
        binding.notificacionesTab.setOnClickListener {
            updateTabSelection(true)
        }

        binding.susurrosTab.setOnClickListener {
            updateTabSelection(false)
        }
    }

    private fun updateTabSelection(notificacionesSelected: Boolean) {
        if (notificacionesSelected) {
            binding.notificacionesTab.setTextColor(resources.getColor(R.color.purple_500, null))
            binding.susurrosTab.setTextColor(resources.getColor(R.color.white, null))
            binding.tabIndicator.apply {
                val params = layoutParams as ViewGroup.LayoutParams
                layoutParams = params
            }
        } else {
            binding.notificacionesTab.setTextColor(resources.getColor(R.color.white, null))
            binding.susurrosTab.setTextColor(resources.getColor(R.color.purple_500, null))
            binding.tabIndicator.apply {
                val params = layoutParams as ViewGroup.LayoutParams
                layoutParams = params
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}