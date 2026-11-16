package com.dayflow.app

import com.dayflow.app.data.*
import java.util.Calendar

/**
 * Test-only fixture: reproduces exactly what early test builds wrote on first launch (the app no longer ships it).
 * [installOffset] is the install day relative to today (0 = installed today, -3 = three days ago).
 */
object TestData {
    private fun day(base: Int, n: Int): Calendar = addDays(base + n)

    suspend fun legacySeed(installOffset: Int = 0) {
        val dao = Repo.dao
        val inst = dstr(day(installOffset, 0)); val now = day(installOffset, 0).timeInMillis
        listOf(
            Habit("water","Drink water","💧","#39D0D8","count",8,"glasses","0|1|2|3|4|5|6",null,true,0),
            Habit("walk","Morning walk","🚶","#4ADE80","tick",1,"","0|1|2|3|4|5|6","06:45",true,1),
            Habit("meditate","Meditate 10 min","🧘","#5EA8FF","tick",1,"","0|1|2|3|4|5|6","07:15",true,2),
            Habit("gym","Gym workout","🏋️","#FF8A5C","tick",1,"","1|3|5|6","18:00",true,3),
            Habit("read","Read","📖","#8B7BFF","count",20,"pages","0|1|2|3|4|5|6","21:30",true,4),
            Habit("sleepby","Lights out by 11","🌙","#F472B6","tick",1,"","0|1|2|3|4|5|6","23:00",false,5)
        ).forEach { dao.putHabit(it) }
        for (i in 6 downTo 1) {
            val c = day(installOffset, -i); val d = dstr(c); val dw = dow(c)
            dao.putLog(HabitLog(d,"water",5 + i % 4))
            dao.putLog(HabitLog(d,"walk",if (i % 5 == 0) 0 else 1))
            dao.putLog(HabitLog(d,"meditate",if (i % 3 == 0) 1 else 0))
            if (dw in listOf(1,3,5,6)) dao.putLog(HabitLog(d,"gym",if (i % 4 == 0) 0 else 1))
            dao.putLog(HabitLog(d,"read",8 + (i*2) % 13))
            val mins = 390 + (i*17) % 120
            dao.putSleep(SleepEntry(uid(), d, c.timeInMillis - mins*60000L, c.timeInMillis, mins))
            dao.putRun(MomentRun(uid(),"m-wake",d,c.timeInMillis,5,5,"",""))
            dao.putRun(MomentRun(uid(),"m-home",d,c.timeInMillis,4,5,"Log water",""))
            if (dw in 1..5) dao.putRun(MomentRun(uid(),"m-office",d,c.timeInMillis,3,5,"ID card|Laptop + charger",""))
            if (i == 4 || i == 2) dao.putRun(MomentRun(uid(),"m-market",d,c.timeInMillis,2,4,"Cloth bag|Wallet / UPI",""))
        }
        dao.putLog(HabitLog(inst,"water",3)); dao.putLog(HabitLog(inst,"walk",1)); dao.putLog(HabitLog(inst,"meditate",1))
        val ts = listOf(
            Task(uid(),"Prepare presentation","checklist",inst,"10:30",null,"none",true,false,null,now-86400000,0),
            Task(uid(),"Pay electricity bill","note",inst,"17:00",null,"none",true,false,null,now-86400000,1),
            Task(uid(),"Buy groceries","checklist",null,null,"m-market","none",false,false,null,now-7200000,2),
            Task(uid(),"Call mom","note",null,"20:00",null,"daily",true,false,null,now-3600000,3),
            Task(uid(),"Pick up vitamins","note",null,null,"m-pharmacy","none",false,false,null,now-3600000,4))
        ts.forEach { dao.putTask(it) }
        listOf("Draft slides" to true,"Rehearse once" to false,"Export PDF" to false).forEachIndexed { i, p ->
            dao.putItem(TaskItem(uid(), ts[0].id, p.first, p.second, i)) }
        listOf("Milk 1L","Eggs ×6","Vegetables","Fruits").forEachIndexed { i, s -> dao.putItem(TaskItem(uid(), ts[2].id, s, false, i)) }
        listOf(
            Moment("m-wake","wake","routine","I woke up","🌅","#FFB347","wake|wake up|woke up|walk up|good morning|morning",0,null,0),
            Moment("m-office","office","routine","Before office","🏢","#5EA8FF","office|work|leaving for office|going to office",0,null,1),
            Moment("m-home","home","routine","Back home","🏡","#4ADE80","home|back home|reached home|returned",0,null,2),
            Moment("m-sleep","sleep","routine","Going to sleep","🌙","#8B7BFF","sleep|night|bed|going to bed|good night",0,null,3),
            Moment("m-market",null,"place","Market","🛒","#F472B6","market|grocery|shop|shopping",0,null,4),
            Moment("m-gym",null,"place","Gym","🏋️","#FF8A5C","gym|workout",0,null,5),
            Moment("m-pharmacy",null,"place","Pharmacy","💊","#39D0D8","pharmacy|medical|chemist",0,null,6)
        ).forEach { dao.putMoment(it) }
        dao.delMItemsOf("m-wake"); dao.delMItemsOf("m-office"); dao.delMItemsOf("m-home"); dao.delMItemsOf("m-sleep")
        mapOf(
            "m-wake" to listOf("Drink a glass of water","Brush & freshen up","5-minute stretch","Make the bed","Check today's plan"),
            "m-office" to listOf("ID card","Laptop + charger","Keys & wallet","Water bottle","Check today's meetings"),
            "m-home" to listOf("Wash up","Log water","Gym if scheduled","Family time","Prep tomorrow's bag"),
            "m-sleep" to listOf("Phone on charge","Set alarm","Review tomorrow","Lights off","Gratitude note"),
            "m-market" to listOf("Cloth bag","Shopping list","Wallet / UPI","Compare prices"),
            "m-gym" to listOf("Water bottle","Towel","Shoes check","Playlist"),
            "m-pharmacy" to listOf("Prescription slip","Wallet","Ask dosage")
        ).forEach { (k, l) -> l.forEachIndexed { i, s -> dao.putMItem(MomentItem(uid(), k, s, i)) } }
        dao.putMed(Medicine("med-example","Example medicine","1 tablet","09:00|21:00",10,true,false,now))
        dao.putView(SavedView(uid(),"🛒 Market run","all","m-market","","manual"))
        dao.putView(SavedView(uid(),"⏰ Timed today","timed","all","","time"))
    }
}
