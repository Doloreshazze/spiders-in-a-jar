package com.playeverywhere.spiders

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(JarView())
    }

    private enum class Tactic { ATTACK, DEFEND, FLEE }
    private enum class Species { ROCK, SCISSORS, PAPER }

    private data class Spider(
        var x: Float, var y: Float, var energy: Float, var angle: Float,
        var speed: Float, val id: Int, val species: Species,
        var tactic: Tactic = Tactic.DEFEND
    )

    inner class JarView : View(this) {
        private val spiders = mutableListOf<Spider>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var last = System.nanoTime()
        private var paused = false
        private var nextId = 1

        init {
            repeat(18) { addSpider() }
            setOnClickListener { paused = !paused; invalidate() }
        }

        private fun addSpider() {
            val species = when (nextId % 3) {
                1 -> Species.ROCK
                2 -> Species.SCISSORS
                else -> Species.PAPER
            }
            spiders += Spider(
                Random.nextFloat() * max(width.toFloat(), 900f),
                Random.nextFloat() * max(height.toFloat(), 1500f),
                45f + Random.nextFloat() * 55f,
                Random.nextFloat() * 6.28f,
                35f + Random.nextFloat() * 45f,
                nextId++, species
            )
        }

        private fun beats(a: Species, b: Species): Boolean =
            (a == Species.ROCK && b == Species.SCISSORS) ||
            (a == Species.SCISSORS && b == Species.PAPER) ||
            (a == Species.PAPER && b == Species.ROCK)

        override fun onDraw(c: Canvas) {
            val now = System.nanoTime()
            val dt = min((now - last) / 1e9f, .05f)
            last = now
            c.drawColor(Color.rgb(13, 17, 15))
            if (!paused) update(dt)
            drawJar(c)
            spiders.forEach { drawSpider(c, it) }
            drawHud(c)
            postInvalidateDelayed(16)
        }

        private fun angleDiff(a: Float, b: Float): Float {
            var d = (a - b + PI.toFloat()) % (2f * PI.toFloat()) - PI.toFloat()
            if (d < -PI) d += 2f * PI.toFloat()
            return abs(d)
        }

        private fun update(dt: Float) {
            val w = width.toFloat()
            val h = height.toFloat()
            val dead = mutableListOf<Spider>()

            for (s in spiders) {
                s.energy -= dt * 2.0f
                var prey: Spider? = null
                var preyDist = Float.MAX_VALUE
                var threat: Spider? = null
                var threatDist = Float.MAX_VALUE

                for (o in spiders) {
                    if (o === s || o.energy <= 0f) continue
                    val d = hypot(o.x - s.x, o.y - s.y)
                    if (d < preyDist && beats(s.species, o.species) &&
                        o.energy < s.energy * 1.20f) {
                        preyDist = d
                        prey = o
                    }
                    if (d < threatDist && beats(o.species, s.species) &&
                        o.energy > s.energy * 0.75f) {
                        threatDist = d
                        threat = o
                    }
                }

                if (threat != null && threatDist < 175f) {
                    if (s.energy < threat!!.energy * 1.05f || threatDist < 95f) {
                        s.tactic = Tactic.FLEE
                        s.angle = atan2(s.y - threat!!.y, s.x - threat!!.x)
                    } else {
                        s.tactic = Tactic.DEFEND
                        val toThreat = atan2(threat!!.y - s.y, threat!!.x - s.x)
                        if (angleDiff(toThreat, s.angle) > Math.toRadians(105.0) ||
                            Random.nextFloat() < dt * 2.5f) s.angle = toThreat
                    }
                } else if (prey != null && preyDist < 280f) {
                    val toPrey = atan2(prey!!.y - s.y, prey!!.x - s.x)
                    if (angleDiff(toPrey, s.angle) < Math.toRadians(95.0)) {
                        s.tactic = Tactic.ATTACK
                        s.angle = toPrey
                    } else {
                        s.tactic = Tactic.DEFEND
                        s.angle += dt * 3.0f
                    }
                } else {
                    s.tactic = Tactic.DEFEND
                    if (Random.nextFloat() < dt * .8f)
                        s.angle += (Random.nextFloat() - .5f) * 1.8f
                }

                val moveSpeed = when (s.tactic) {
                    Tactic.ATTACK -> s.speed * 1.20f
                    Tactic.DEFEND -> s.speed * .72f
                    Tactic.FLEE -> s.speed * 1.75f
                }
                s.x += cos(s.angle) * moveSpeed * dt
                s.y += sin(s.angle) * moveSpeed * dt

                val p = 42f
                if (s.x < p) { s.x = p; s.angle = PI.toFloat() - s.angle }
                if (s.x > w - p) { s.x = w - p; s.angle = PI.toFloat() - s.angle }
                if (s.y < p) { s.y = p; s.angle = -s.angle }
                if (s.y > h - p) { s.y = h - p; s.angle = -s.angle }

                if (prey != null && preyDist < 34f && s.tactic == Tactic.ATTACK &&
                    prey!!.energy > 0f) {
                    val toPrey = atan2(prey!!.y - s.y, prey!!.x - s.x)
                    if (angleDiff(toPrey, s.angle) < Math.toRadians(75.0)) {
                        val preyFacingUs = atan2(s.y - prey!!.y, s.x - prey!!.x)
                        val rearHit = angleDiff(preyFacingUs, prey!!.angle) > Math.toRadians(105.0)
                        val damage = if (rearHit) 52f else 32f
                        prey!!.energy -= dt * damage
                        s.energy = min(120f, s.energy + dt * 24f)
                    }
                }

                if (threat != null && threatDist < 38f &&
                    threat!!.tactic == Tactic.ATTACK && s.energy > 0f) {
                    val attackerToUs = atan2(s.y - threat!!.y, s.x - threat!!.x)
                    val facingAttacker = angleDiff(attackerToUs, s.angle) < Math.toRadians(80.0)
                    if (facingAttacker) s.energy -= dt * 10f
                }
                if (s.energy <= 0f) dead += s
            }

            spiders.removeAll(dead.toSet())
            while (spiders.size < 8) addSpider()
        }

        private fun speciesColor(s: Species): Int = when (s) {
            Species.ROCK -> Color.rgb(155, 155, 165)
            Species.SCISSORS -> Color.rgb(235, 95, 105)
            Species.PAPER -> Color.rgb(95, 175, 225)
        }

        private fun drawJar(c: Canvas) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f
            paint.color = Color.argb(150, 190, 220, 205)
            c.drawRoundRect(14f, 14f, width - 14f, height - 14f, 42f, 42f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(35, 255, 255, 255)
            c.drawRoundRect(25f, 25f, width - 25f, height - 25f, 35f, 35f, paint)
        }

        private fun drawSpider(c: Canvas, s: Spider) {
            val r = 7f + min(9f, s.energy / 15f)
            paint.style = Paint.Style.FILL
            paint.color = speciesColor(s.species)
            c.drawCircle(s.x, s.y, r, paint)
            paint.color = when (s.tactic) {
                Tactic.ATTACK -> Color.rgb(245, 75, 55)
                Tactic.DEFEND -> Color.rgb(80, 110, 125)
                Tactic.FLEE -> Color.rgb(245, 215, 65)
            }
            c.drawCircle(s.x, s.y, r * .30f, paint)

            val eyeX = cos(s.angle) * r * .55f
            val eyeY = sin(s.angle) * r * .55f
            paint.color = Color.WHITE
            c.drawCircle(s.x + eyeX + cos(s.angle + .45f) * 2f,
                s.y + eyeY + sin(s.angle + .45f) * 2f, 2.2f, paint)
            c.drawCircle(s.x + eyeX + cos(s.angle - .45f) * 2f,
                s.y + eyeY + sin(s.angle - .45f) * 2f, 2.2f, paint)

            paint.color = Color.DKGRAY
            paint.strokeWidth = 2.2f
            paint.style = Paint.Style.STROKE
            repeat(4) { i ->
                val a = s.angle + PI.toFloat() / 2f * (i - 1.5f)
                c.drawLine(s.x + cos(a) * r * .4f, s.y + sin(a) * r * .4f,
                    s.x + cos(a) * r * 2.3f, s.y + sin(a) * r * 2.3f, paint)
                c.drawLine(s.x + cos(a) * r * .4f, s.y + sin(a) * r * .4f,
                    s.x + cos(a + .65f) * r * 2f, s.y + sin(a + .65f) * r * 2f, paint)
            }
            paint.style = Paint.Style.FILL
        }

        private fun drawHud(c: Canvas) {
            paint.color = Color.WHITE
            paint.textSize = 42f
            paint.typeface = Typeface.DEFAULT_BOLD
            c.drawText("ПАУКИ В БАНКЕ", 38f, 65f, paint)
            paint.textSize = 25f
            paint.typeface = Typeface.DEFAULT
            c.drawText("Живых: " + spiders.size, 38f, 103f, paint)
            paint.textSize = 19f
            c.drawText("Камень > Ножницы   Ножницы > Бумага   Бумага > Камень", 38f, 136f, paint)
            c.drawText("Серый: К   Красный: Н   Синий: Б   Точка = тактика", 38f, 162f, paint)
            c.drawText(if (paused) "ПАУЗА — нажми экран" else "Нажми экран: пауза",
                38f, height - 35f, paint)
        }
    }
}
