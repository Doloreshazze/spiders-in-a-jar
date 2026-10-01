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
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(JarView())
    }

    private data class Spider(var x:Float,var y:Float,var energy:Float,var angle:Float,var speed:Float,val id:Int)

    inner class JarView : View(this) {
        var x:Float,var y:Float,var energy:Float,var angle:Float,var speed:Float,val id:Int)
        private val spiders=mutableListOf<Spider>()
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private var last=System.nanoTime()
        private var paused=false
        private var nextId=1
        init { repeat(18){addSpider()}; setOnClickListener{paused=!paused;invalidate()} }
        private fun addSpider(){spiders+=Spider(Random.nextFloat()*max(width.toFloat(),900f),Random.nextFloat()*max(height.toFloat(),1500f),45f+Random.nextFloat()*55f,Random.nextFloat()*6.28f,35f+Random.nextFloat()*45f,nextId++)}
        override fun onDraw(c:Canvas){
            val now=System.nanoTime(); val dt=min((now-last)/1e9f,.05f); last=now
            c.drawColor(Color.rgb(13,17,15)); if(!paused) update(dt); drawJar(c); spiders.forEach{drawSpider(c,it)}; drawHud(c); postInvalidateDelayed(16)
        }
        private fun update(dt:Float){
            val w=width.toFloat();val h=height.toFloat();val dead=mutableListOf<Spider>()
            for(s in spiders){
                s.energy-=dt*2.1f
                var target:Spider?=null;var best=Float.MAX_VALUE
                for(o in spiders)if(o!==s&&o.energy>0){val d=hypot(o.x-s.x,o.y-s.y);if(d<best){best=d;target=o}}
                if(target!=null&&best<280f)s.angle=atan2(target!!.y-s.y,target!!.x-s.x) else if(Random.nextFloat()<dt*.8f)s.angle+=(Random.nextFloat()-.5f)*1.4f
                s.x+=cos(s.angle)*s.speed*dt;s.y+=sin(s.angle)*s.speed*dt
                val p=42f
                if(s.x<p){s.x=p;s.angle=PI.toFloat()-s.angle};if(s.x>w-p){s.x=w-p;s.angle=PI.toFloat()-s.angle}
                if(s.y<p){s.y=p;s.angle=-s.angle};if(s.y>h-p){s.y=h-p;s.angle=-s.angle}
                if(target!=null&&best<28f&&target!!.energy>0){target!!.energy-=dt*38f;s.energy=min(120f,s.energy+dt*28f)}
                if(s.energy<=0)dead+=s
            }
            spiders.removeAll(dead.toSet());while(spiders.size<8)addSpider()
        }
        private fun drawJar(c:Canvas){paint.style=Paint.Style.STROKE;paint.strokeWidth=8f;paint.color=Color.argb(150,190,220,205);c.drawRoundRect(14f,14f,width-14f,height-14f,42f,42f,paint);paint.style=Paint.Style.FILL;paint.color=Color.argb(35,255,255,255);c.drawRoundRect(25f,25f,width-25f,height-25f,35f,35f,paint)}
        private fun drawSpider(c:Canvas,s:Spider){
            val r=7f+min(9f,s.energy/15f);paint.color=if(s.energy>55)Color.rgb(210,150,90)else Color.rgb(145,105,85);paint.style=Paint.Style.FILL;c.drawCircle(s.x,s.y,r,paint);paint.strokeWidth=2.2f;paint.style=Paint.Style.STROKE
            repeat(4){i->val a=s.angle+PI.toFloat()/2f*(i-1.5f);c.drawLine(s.x+cos(a)*r*.4f,s.y+sin(a)*r*.4f,s.x+cos(a)*r*2.3f,s.y+sin(a)*r*2.3f,paint);c.drawLine(s.x+cos(a)*r*.4f,s.y+sin(a)*r*.4f,s.x+cos(a+.65f)*r*2f,s.y+sin(a+.65f)*r*2f,paint)};paint.style=Paint.Style.FILL
        }
        private fun drawHud(c:Canvas){paint.color=Color.WHITE;paint.textSize=42f;paint.typeface=Typeface.DEFAULT_BOLD;c.drawText("ПАУКИ В БАНКЕ",38f,65f,paint);paint.textSize=27f;paint.typeface=Typeface.DEFAULT;c.drawText("Живых: ${spiders.size}",38f,103f,paint);c.drawText(if(paused)"ПАУЗА — нажми экран" else "Нажми экран: пауза",38f,height-35f,paint)}
    }
}
