package com.playeverywhere.spiders

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.graphics.*
import android.widget.*
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val root = FrameLayout(this)
        val game = JarView()
        root.addView(game)

        val settings = Button(this).apply {
            text = "⚙ Пауки: 18"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(180, 35, 35, 35))
            setOnClickListener { showSpiderSettings(game, this) }
        }
        val lp = FrameLayout.LayoutParams(210, 64)
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.END
        lp.setMargins(0, 18, 18, 0)
        root.addView(settings, lp)

        val modeButton = Button(this).apply {
            text = "РЕЖИМ: RPS"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(220, 35, 35, 35))
            setOnClickListener {
                game.evolutionMode = !game.evolutionMode
                text = if (game.evolutionMode) "РЕЖИМ: ЭВОЛЮЦИЯ" else "РЕЖИМ: RPS"
                game.resetPopulation()
            }
        }
        val mlp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 72
        )
        mlp.gravity = android.view.Gravity.BOTTOM
        mlp.setMargins(24, 0, 24, 105)
        root.addView(modeButton, mlp)
        setContentView(root)
    }

    private fun showSpiderSettings(game: JarView, button: Button) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        val label = TextView(this)
        label.text = "Количество пауков: " + game.spiderCount
        label.textSize = 20f
        val seek = SeekBar(this).apply {
            max = 97
            progress = game.spiderCount.coerceIn(3, 100) - 3
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar, p: Int, fromUser: Boolean) {
                label.text = "Количество пауков: " + (p + 3)
            }
            override fun onStartTrackingTouch(b: SeekBar) {}
            override fun onStopTrackingTouch(b: SeekBar) {}
        })
        layout.addView(label)
        layout.addView(seek)

        AlertDialog.Builder(this)
            .setTitle("Настройки симуляции")
            .setView(layout)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Применить") { _, _ ->
                val count = seek.progress + 3
                game.setSpiderCount(count)
                button.text = "⚙ Пауки: " + count
            }
            .show()
    }

    private enum class Tactic { ATTACK, DEFEND, FLEE }
    private enum class Species { ROCK, SCISSORS, PAPER }

    private data class Spider(
        var x: Float, var y: Float, var energy: Float, var angle: Float,
        var speed: Float, val id: Int, val species: Species,
        var gene: Float = Random.nextFloat(),
        var age: Float = 0f,
        var kills: Int = 0,
        var lineage: Int = 1,
        var tactic: Tactic = Tactic.DEFEND
    )

    inner class JarView : View(this) {
        private val spiders = mutableListOf<Spider>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var last = System.nanoTime()
        private var paused = false
        private var nextId = 1
        var spiderCount = 18
            private set
        var evolutionMode = false
        private var generation = 1
        private var bestGene = 0.5f
        private var bestFitness = 0f
        private var births = 0

        fun setSpiderCount(count: Int) {
            spiderCount = count.coerceIn(3, 100)
            resetPopulation()
        }

        fun resetPopulation() {
            spiders.clear()
            nextId = 1
            generation = 1
            bestGene = 0.5f
            bestFitness = 0f
            births = 0
            repeat(spiderCount) { addSpider() }
            invalidate()
        }

        init {
            repeat(spiderCount) { addSpider() }
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
                nextId++, species, Random.nextFloat()
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
            if (evolutionMode) {
                updateEvolution(dt)
                return
            }
            val w = width.toFloat()
            val h = height.toFloat()
            val dead = mutableListOf<Spider>()

            val reproductionQueue = mutableListOf<Spider>()

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
            while (spiders.size < spiderCount) addSpider()
        }


        // One number is the spider's genome. It is decoded into several
        // behavioral traits, so natural selection can tune a single gene.
        private fun trait(gene: Float, salt: Int): Float {
            val x = sin((gene * 997.0 + salt * 83.17).toDouble()) * 43758.5453
            return (x - floor(x)).toFloat()
        }

        private fun aggression(s: Spider) = 0.25f + trait(s.gene, 1) * 1.15f
        private fun caution(s: Spider) = 0.15f + trait(s.gene, 2) * 1.10f
        private fun perception(s: Spider) = 90f + trait(s.gene, 3) * 240f
        private fun mobility(s: Spider) = 0.55f + trait(s.gene, 4) * 1.55f
        private fun metabolism(s: Spider) = 1.25f + trait(s.gene, 5) * 1.55f

        private fun updateEvolution(dt: Float) {
            val w = width.toFloat()
            val h = height.toFloat()

            // Evolution is now an emergent consequence of the game itself:
            // hunger, hunting, fleeing, death and reproduction determine which
            // genomes leave descendants. There is no timed "selection" event.
            val crowding = max(0f, (spiders.size - spiderCount).toFloat() / spiderCount)
            val reproductionQueue = mutableListOf<Spider>()

            for (s in spiders) {
                s.age += dt
                s.energy -= dt * metabolism(s) * (1f + crowding * 0.65f)

                var nearest: Spider? = null
                var nearestDist = Float.MAX_VALUE
                var weakest: Spider? = null
                var weakestDist = Float.MAX_VALUE

                for (o in spiders) {
                    if (o === s || o.energy <= 0f) continue
                    val d = hypot(o.x - s.x, o.y - s.y)
                    if (d < nearestDist) {
                        nearestDist = d
                        nearest = o
                    }
                    if (o.energy < s.energy && d < weakestDist) {
                        weakestDist = d
                        weakest = o
                    }
                }

                val ag = aggression(s)
                val ca = caution(s)
                val range = perception(s)

                if (nearest != null && nearestDist < range) {
                    val n = nearest!!
                    val toward = atan2(n.y - s.y, n.x - s.x)
                    if (n.energy < s.energy * (0.65f + ag * .45f) && ag > ca) {
                        s.tactic = Tactic.ATTACK
                        s.angle += angleDelta(toward - s.angle) *
                            min(1f, dt * (2.5f + mobility(s)))
                    } else if (n.energy > s.energy * (1.0f + ca * .35f)) {
                        s.tactic = Tactic.FLEE
                        s.angle = atan2(s.y - n.y, s.x - n.x)
                    } else {
                        s.tactic = Tactic.DEFEND
                        s.angle += dt * (if (ag > .8f) 1.8f else -1.2f)
                    }
                } else {
                    s.tactic = Tactic.DEFEND
                    if (Random.nextFloat() < dt * (0.35f + mobility(s) * .3f)) {
                        s.angle += (Random.nextFloat() - .5f) * 2.5f
                    }
                }

                // Successful hunting transfers energy from prey to hunter.
                if (weakest != null && weakestDist < 38f &&
                    s.tactic == Tactic.ATTACK && weakest!!.energy > 0f) {
                    val damage = (18f + 24f * ag) * dt
                    weakest!!.energy -= damage
                    s.energy = min(120f, s.energy + damage * .48f)
                    if (weakest!!.energy <= 0f) s.kills++
                }

                val speed = s.speed * mobility(s) * when (s.tactic) {
                    Tactic.ATTACK -> 1.35f
                    Tactic.DEFEND -> .72f
                    Tactic.FLEE -> 1.85f
                }
                s.x += cos(s.angle) * speed * dt
                s.y += sin(s.angle) * speed * dt

                val p = 42f
                if (s.x < p) { s.x = p; s.angle = PI.toFloat() - s.angle }
                if (s.x > w - p) { s.x = w - p; s.angle = PI.toFloat() - s.angle }
                if (s.y < p) { s.y = p; s.angle = -s.angle }
                if (s.y > h - p) { s.y = h - p; s.angle = -s.angle }

                // Reproduction costs real energy. Only spiders that have
                // survived and accumulated enough energy can reproduce.
                // Their child inherits the genome with a small mutation.
                if (s.age > 4f && s.energy > 92f &&
                    spiders.size < spiderCount * 2 &&
                    Random.nextFloat() < dt * (0.045f + ag * 0.035f)) {
                    s.energy -= 46f
                    reproductionQueue += s
                    births++
                }
            }

            // Add offspring only after iteration is complete. This avoids modifying
            // the ArrayList while its iterator is active.
            reproductionQueue.forEach { parent -> addEvolutionChild(parent) }

            // Death is entirely caused by the simulation mechanics.
            spiders.removeAll { it.energy <= 0f }

            // Track the naturally achieved best fitness, not a forced ranking.
            spiders.maxByOrNull { fitness(it) }?.let { best ->
                if (fitness(best) > bestFitness) {
                    bestFitness = fitness(best)
                    bestGene = best.gene
                }
                generation = max(generation, best.lineage)
            }
        }

        private fun angleDelta(a: Float): Float {
            var d = (a + PI.toFloat()) % (2f * PI.toFloat()) - PI.toFloat()
            if (d < -PI) d += 2f * PI.toFloat()
            return d
        }

        private fun fitness(s: Spider): Float {
            return s.energy + s.kills * 48f + min(35f, s.age * 1.2f)
        }

        // Child production is local and continuous: no generation-wide cull.
        private fun addEvolutionChild(parent: Spider) {
            val mutation = (Random.nextFloat() - .5f) * .10f
            val childGene = (parent.gene + mutation).coerceIn(0f, 1f)
            val child = Spider(
                Random.nextFloat() * max(width.toFloat() - 84f, 100f) + 42f,
                Random.nextFloat() * max(height.toFloat() - 84f, 100f) + 42f,
                42f + Random.nextFloat() * 12f,
                Random.nextFloat() * 6.28f,
                (parent.speed + (Random.nextFloat() - .5f) * 8f).coerceIn(25f, 85f),
                nextId++, Species.ROCK, childGene,
                age = 0f,
                kills = 0,
                lineage = parent.lineage + 1
            )
            spiders += child
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
            paint.color = if (evolutionMode) {
                val g = s.gene
                Color.rgb(
                    (70 + 170 * trait(g, 10)).toInt(),
                    (80 + 150 * trait(g, 11)).toInt(),
                    (90 + 150 * trait(g, 12)).toInt()
                )
            } else speciesColor(s.species)
            c.drawCircle(s.x, s.y, r, paint)
            paint.color = when (s.tactic) {
                Tactic.ATTACK -> Color.rgb(245, 75, 55)
                Tactic.DEFEND -> Color.rgb(80, 110, 125)
                Tactic.FLEE -> Color.rgb(245, 215, 65)
            }
            c.drawCircle(s.x, s.y, r * .30f, paint)
            if (evolutionMode) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = Color.argb(180, 255, 255, 255)
                c.drawArc(s.x - r - 3f, s.y - r - 3f, s.x + r + 3f, s.y + r + 3f,
                    -90f, 360f * s.gene, false, paint)
                paint.style = Paint.Style.FILL
            }

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
            if (evolutionMode) {
                c.drawText("🧬 ПОКОЛЕНИЕ: $generation    Рождений: $births", 38f, 136f, paint)
                c.drawText("Лучший ген: %.3f   Приспособленность: %.0f".format(bestGene, bestFitness),
                    38f, 162f, paint)
                c.drawText("Отбор естественный: выживание → энергия → потомство → мутация.", 38f, 188f, paint)
                c.drawText("Дуга = геном. Точка = тактика.", 38f, 214f, paint)
            } else {
                c.drawText("Камень > Ножницы   Ножницы > Бумага   Бумага > Камень", 38f, 136f, paint)
                c.drawText("Серый: К   Красный: Н   Синий: Б   Точка = тактика", 38f, 162f, paint)
            }
            c.drawText(if (paused) "ПАУЗА — нажми экран" else "Нажми экран: пауза",
                38f, height - 35f, paint)
        }
    }
}
