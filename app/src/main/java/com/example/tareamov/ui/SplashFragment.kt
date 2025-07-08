package com.example.tareamov.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tareamov.R
import com.example.tareamov.util.SessionManager

class SplashFragment : Fragment() {    private val splashTimeOut: Long = 4000 // 4 seconds for better experience
    private lateinit var letterViews: List<TextView>
    private lateinit var particleViews: List<ImageView>
    private lateinit var decorViews: List<ImageView>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Log to verify the fragment is being created
        Log.d("SplashFragment", "onCreateView called")
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("SplashFragment", "onViewCreated called")

        initializeViews(view)
        startSpectacularAnimation()
        
        // Navigate after animation sequence
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, splashTimeOut)
    }

    private fun initializeViews(view: View) {
        // Initialize letter views
        letterViews = listOf(
            view.findViewById(R.id.letter_t),
            view.findViewById(R.id.letter_a1),
            view.findViewById(R.id.letter_r),
            view.findViewById(R.id.letter_e1),
            view.findViewById(R.id.letter_a2),
            view.findViewById(R.id.letter_m),
            view.findViewById(R.id.letter_o),
            view.findViewById(R.id.letter_v)
        )

        // Initialize particle views
        particleViews = listOf(
            view.findViewById(R.id.particle1),
            view.findViewById(R.id.particle2),
            view.findViewById(R.id.particle3),
            view.findViewById(R.id.particle4)
        )

        // Initialize decorative views
        decorViews = listOf(
            view.findViewById(R.id.decorIcon1),
            view.findViewById(R.id.decorIcon2),
            view.findViewById(R.id.decorIcon3)
        )
    }

    private fun startSpectacularAnimation() {
        val logoImage = view?.findViewById<ImageView>(R.id.splashLogo)
        val progressBar = view?.findViewById<ProgressBar>(R.id.loadingProgressBar)
        val loadingTextView = view?.findViewById<TextView>(R.id.loadingTextView)

        // Step 1: Animate logo entrance (0-800ms)
        logoImage?.let { logo ->
            animateLogoEntrance(logo)
        }

        // Step 2: Animate letters one by one (800-2800ms)
        Handler(Looper.getMainLooper()).postDelayed({
            animateLettersSequence()
        }, 800)

        // Step 3: Animate particles (1500ms)
        Handler(Looper.getMainLooper()).postDelayed({
            animateParticles()
        }, 1500)

        // Step 4: Animate decorative elements (2000ms)
        Handler(Looper.getMainLooper()).postDelayed({
            animateDecorativeElements()
        }, 2000)

        // Step 5: Show progress bar and loading text (2500ms)
        Handler(Looper.getMainLooper()).postDelayed({
            progressBar?.let { animateProgressBar(it) }
            loadingTextView?.let { animateLoadingText(it) }
        }, 2500)
    }

    private fun animateLogoEntrance(logo: ImageView) {
        // Create spectacular logo entrance animation
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0f, 1.2f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0f, 1.2f, 1f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f)
        val rotation = PropertyValuesHolder.ofFloat(View.ROTATION, 0f, 360f)

        val logoAnimator = ObjectAnimator.ofPropertyValuesHolder(logo, scaleX, scaleY, alpha, rotation)
        logoAnimator.duration = 800
        logoAnimator.interpolator = OvershootInterpolator(1.5f)
        logoAnimator.start()

        // Add a subtle pulse effect that continues
        Handler(Looper.getMainLooper()).postDelayed({
            startLogoPulse(logo)
        }, 800)
    }

    private fun startLogoPulse(logo: ImageView) {
        val pulseAnimator = ObjectAnimator.ofFloat(logo, "scaleX", 1f, 1.05f, 1f)
        val pulseAnimatorY = ObjectAnimator.ofFloat(logo, "scaleY", 1f, 1.05f, 1f)
        
        pulseAnimator.duration = 2000
        pulseAnimatorY.duration = 2000
        pulseAnimator.repeatCount = ObjectAnimator.INFINITE
        pulseAnimatorY.repeatCount = ObjectAnimator.INFINITE
        
        pulseAnimator.start()
        pulseAnimatorY.start()
    }

    private fun animateLettersSequence() {
        letterViews.forEachIndexed { index, letter ->
            Handler(Looper.getMainLooper()).postDelayed({
                animateLetter(letter, index)
            }, index * 150L) // 150ms delay between each letter
        }
    }

    private fun animateLetter(letter: TextView, index: Int) {
        // Netflix-style letter animation
        val translateY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 50f, -10f, 0f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f)
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.8f, 1.1f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.8f, 1.1f, 1f)

        val letterAnimator = ObjectAnimator.ofPropertyValuesHolder(letter, translateY, alpha, scaleX, scaleY)
        letterAnimator.duration = 600
        letterAnimator.interpolator = OvershootInterpolator(2f)
        letterAnimator.start()

        // Add a special effect for "Mov" letters (golden color)
        if (index >= 5) { // M, o, v letters
            letterAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    startLetterGlow(letter)
                }
            })
        }
    }

    private fun startLetterGlow(letter: TextView) {
        // Add a subtle color animation for the "Mov" part
        val colorAnimator = ObjectAnimator.ofFloat(letter, "alpha", 1f, 0.7f, 1f)
        colorAnimator.duration = 1500
        colorAnimator.repeatCount = ObjectAnimator.INFINITE
        colorAnimator.start()
    }

    private fun animateParticles() {
        particleViews.forEachIndexed { index, particle ->
            Handler(Looper.getMainLooper()).postDelayed({
                animateParticle(particle)
            }, index * 200L)
        }
    }

    private fun animateParticle(particle: ImageView) {
        // Fade in
        val alpha = ObjectAnimator.ofFloat(particle, "alpha", 0f, 0.8f)
        alpha.duration = 800
        alpha.start()

        // Floating animation
        val translateY = ObjectAnimator.ofFloat(particle, "translationY", 0f, -20f, 0f)
        translateY.duration = 3000
        translateY.repeatCount = ObjectAnimator.INFINITE
        translateY.start()

        // Rotation for sparkles
        if (particle.id == R.id.particle1 || particle.id == R.id.particle2) {
            val rotation = ObjectAnimator.ofFloat(particle, "rotation", 0f, 360f)
            rotation.duration = 4000
            rotation.repeatCount = ObjectAnimator.INFINITE
            rotation.start()
        }
    }

    private fun animateDecorativeElements() {
        decorViews.forEachIndexed { index, decor ->
            Handler(Looper.getMainLooper()).postDelayed({
                animateDecorativeElement(decor)
            }, index * 300L)
        }
    }

    private fun animateDecorativeElement(decor: ImageView) {
        // Spectacular entrance
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0f, 1f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 0.8f)
        val rotation = PropertyValuesHolder.ofFloat(View.ROTATION, 0f, 360f)

        val decorAnimator = ObjectAnimator.ofPropertyValuesHolder(decor, scaleX, scaleY, alpha, rotation)
        decorAnimator.duration = 800
        decorAnimator.interpolator = OvershootInterpolator(1.2f)
        decorAnimator.start()

        // Continuous floating animation
        decorAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                startDecorativeFloat(decor)
            }
        })
    }

    private fun startDecorativeFloat(decor: ImageView) {
        val translateY = ObjectAnimator.ofFloat(decor, "translationY", 0f, -15f, 0f)
        val rotation = ObjectAnimator.ofFloat(decor, "rotation", 0f, 360f)
        
        translateY.duration = 4000
        rotation.duration = 8000
        translateY.repeatCount = ObjectAnimator.INFINITE
        rotation.repeatCount = ObjectAnimator.INFINITE
        
        translateY.start()
        rotation.start()
    }

    private fun animateProgressBar(progressBar: ProgressBar) {
        // Fade in progress bar
        val alpha = ObjectAnimator.ofFloat(progressBar, "alpha", 0f, 1f)
        alpha.duration = 500
        alpha.start()

        // Animate progress
        val progressAnimator = ValueAnimator.ofInt(0, 100)
        progressAnimator.duration = 1500
        progressAnimator.addUpdateListener { animator ->
            progressBar.progress = animator.animatedValue as Int
        }
        progressAnimator.start()
    }

    private fun animateLoadingText(loadingText: TextView) {
        val alpha = ObjectAnimator.ofFloat(loadingText, "alpha", 0f, 1f)
        val translateY = ObjectAnimator.ofFloat(loadingText, "translationY", 20f, 0f)
        
        alpha.duration = 600
        translateY.duration = 600
        
        alpha.start()
        translateY.start()
    }

    private fun navigateToNextScreen() {
        if (isAdded && !isDetached && !isRemoving) {
            try {
                val sessionManager = SessionManager.getInstance(requireContext())
                if (sessionManager.isLoggedIn()) {
                    Log.d("SplashFragment", "Session found, navigating to videoHomeFragment")
                    findNavController().navigate(R.id.action_splashFragment_to_videoHomeFragment)
                } else {
                    Log.d("SplashFragment", "No session, navigating to loginFragment")
                    findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
                }
            } catch (e: Exception) {
                Log.e("SplashFragment", "Navigation error: ${e.message}")
                try {
                    findNavController().navigate(R.id.loginFragment)
                } catch (e: Exception) {
                    Log.e("SplashFragment", "Direct navigation error: ${e.message}")
                }            }
        }
    }
}