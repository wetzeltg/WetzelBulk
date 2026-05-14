// WetzelBulk - Improved Supersets + Compact Layout
const cycle = ['Push', 'Legs', 'Pull', 'Cardio'];
let currentCycleIndex = 0;

const routine = {
    Push: [
        { name: "Incline Chest Press", sets: 4, pair: "Lateral Raises" },
        { name: "Lateral Raises", sets: 4 },
        { name: "Flat Chest Press", sets: 3, pair: "Military Press" },
        { name: "Military Press", sets: 3 },
        { name: "Tricep Pushdowns", sets: 3, pair: "Skull Crushers" },
        { name: "Skull Crushers", sets: 3 }
    ],
    Legs: [
        { name: "Leg Press", sets: 4 },
        { name: "Romanian Deadlifts (DB)", sets: 3, pair: "Calf Raises" },
        { name: "Calf Raises", sets: 4 },
        { name: "Crunches + Back Extensions", sets: 3 }
    ],
    Pull: [
        { name: "Lat Pulldowns (wide)", sets: 4, pair: "Face Pulls" },
        { name: "Face Pulls", sets: 4 },
        { name: "Cable Rows", sets: 3, pair: "Dumbbell Shrugs" },
        { name: "Dumbbell Shrugs", sets: 3 },
        { name: "Incline DB Curls", sets: 3, pair: "Hammer Curls" },
        { name: "Hammer Curls", sets: 3 }
    ],
    Cardio: [{ name: "Incline Treadmill Walk", sets: 1 }]
};

function startWorkout() {
    const workout = cycle[currentCycleIndex];
    document.getElementById('workout-title').textContent = workout + " Day";
    renderExercises(workout);
}

function renderExercises(workoutType) {
    const list = document.getElementById('exercises-list');
    list.innerHTML = '';

    const exercises = routine[workoutType] || [];

    exercises.forEach((ex, exIndex) => {
        let setsHTML = '';
        for (let s = 1; s <= ex.sets; s++) {
            setsHTML += `
                <div class="set-row" id="set-${exIndex}-${s}">
                    <div style="font-weight:500; min-width:45px;">Set ${s}</div>
                    <div style="display:flex; gap:4px; align-items:center; justify-content:center;">
                        <button onclick="adjust(${exIndex},${s},'w',-5)" style="width:36px;">–</button>
                        <span id="w-${exIndex}-${s}" style="width:48px; text-align:center; font-family:monospace; font-size:16px;">135</span>
                        <button onclick="adjust(${exIndex},${s},'w',5)" style="width:36px;">+</button>
                    </div>
                    <div style="display:flex; gap:4px; align-items:center; justify-content:center;">
                        <button onclick="adjust(${exIndex},${s},'r',-1)" style="width:36px;">–</button>
                        <span id="r-${exIndex}-${s}" style="width:40px; text-align:center; font-family:monospace; font-size:16px;">10</span>
                        <button onclick="adjust(${exIndex},${s},'r',1)" style="width:36px;">+</button>
                    </div>
                    <button onclick="completeSet(${exIndex},${s})" style="background:#16a34a;color:white;border:none;padding:8px 12px;">Done</button>
                </div>`;
        }

        const pairText = ex.pair ? `<div style="color:#16a34a; font-size:13px; margin:6px 0 8px;">🔄 Superset with <strong>${ex.pair}</strong></div>` : '';

        const div = document.createElement('div');
        div.className = 'exercise';
        div.innerHTML = `
            <div class="exercise-header">
                <div class="ex-name">${ex.name}</div>
                <button class="info-btn" onclick="showFormNotes('${ex.name}')">ⓘ</button>
            </div>
            ${pairText}
            ${setsHTML}
        `;
        list.appendChild(div);
    });
}

function adjust(exIndex, setNum, type, delta) {
    const prefix = type === 'w' ? 'w' : 'r';
    const el = document.getElementById(`${prefix}-${exIndex}-${setNum}`);
    if (!el) return;
    let val = parseInt(el.textContent) || (type === 'w' ? 135 : 10);
    val = Math.max(type === 'w' ? 5 : 1, val + delta);
    el.textContent = val;
}

function completeSet(exIndex, setNum) {
    const row = document.getElementById(`set-${exIndex}-${setNum}`);
    if (row) row.style.backgroundColor = '#f0fdf4';
}

function showFormNotes(name) {
    alert(`Form notes for ${name}:\n\n• Full range of motion\n• Controlled lowering (2-3 sec)\n• Strong squeeze at the top`);
}

function finishWorkout() {
    if (confirm("Finish workout and move to next day?")) {
        currentCycleIndex = (currentCycleIndex + 1) % cycle.length;
        startWorkout();
    }
}

// Start the app
startWorkout();