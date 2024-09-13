package com.example.messenger

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.View

class TypingAnimation(private val dot1: View, private val dot2: View, private val dot3: View) {

    private var animatorSet: AnimatorSet? = null

    private fun createAnimator(view: View): AnimatorSet {
        // Анимация изменения размера
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 2f, 1f)
        scaleX.duration = 250
        scaleY.duration = 250

        // Анимация изменения цвета
        val colorAnimator = ObjectAnimator.ofArgb(
            view.background, "tint",
            Color.DKGRAY, Color.WHITE, Color.DKGRAY
        )
        colorAnimator.duration = 350

        // Комбинируем анимации
        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, colorAnimator)
        }
    }

    fun startAnimation() {
        val animator1 = createAnimator(dot1)
        val animator2 = createAnimator(dot2)
        val animator3 = createAnimator(dot3)

        animatorSet = AnimatorSet().apply {
            playSequentially(animator1, animator2, animator3)
            startDelay = 200 // Задержка между сменами точек
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animatorSet != null) {
                        startAnimation() // Запускаем заново для бесконечного эффекта
                    }
                }
            })
        }
        animatorSet?.start()
    }

    fun stopAnimation() {
        animatorSet?.cancel()
        animatorSet = null
    }
}
