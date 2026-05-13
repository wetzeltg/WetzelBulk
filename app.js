// WetzelBulk - Workout Logger
const cycle = ['Push', 'Legs', 'Pull', 'Cardio'];
let currentCycleIndex = 0;

const routine = {
    Push: [
        { name: "Incline Chest Press", sets: 4 },
        { name: "Lateral Raises", sets: 4 },
        { name: "Flat Chest Press", sets: 3 },
        { name: "Military Press", sets: 3 },
        { name: "Tricep Pushdowns", sets: 3 },
        { name: "Skull Crushers", sets: 3 }
    ],
    Legs: [
        { name: "Leg Press", sets: 4 },
        { name: "Romanian Deadlifts (DB)", sets: 3 },
        { name: "Calf Raises", sets: 4 },
        { name: "Crunches + Back Extensions", sets: 3 }
    ],
    Pull: [
        { name: "Lat Pulldowns (wide)", sets: 4 },
        { name: "Face Pulls", sets: 4 },
        { name: "Cable Rows", sets: 3 },
        { name: "Dumbbell Shrugs", sets: 3 },
        { name: "Incline DB Curls", sets: 3 },
        { name: "Hammer Curls", sets: 3 }
    ],
    Cardio: [
        { name: "Incline Treadmill Walk", sets: 1 }
    ]
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
        for (let setNum = 1; setNum <= ex.sets; setNum++) {
            setsHTML += `
                <div class="set-row" id="set-${exIndex}-${setNum}">
                    <div style="font-weight:500;">Set ${setNum}</div>
                    <div>
                        <button onclick="adjustValue(${exIndex},${setNum}, 'weight', -5)">–</button>
                        <span id="w-${exIndex}-${setNum}" style="font-family:monospace; font-size:17px;">135</span>
                        <button onclick="adjustValue(${exIndex},${setNum}, 'weight', 5)">+</button>
                    </div>
                    <div>
                        <button onclick="adjustValue(${exIndex},${setNum}, 'reps', -1)">–</button>
                        <span id="r-${exIndex}-${setNum}" style="font-family:monospace; font-size:17px;">10</span>
                        <button onclick="adjustValue(${exIndex},${setNum}, 'reps', 1)">+</button>
                    </div>
                    <button onclick="completeSet(${exIndex},${setNum})" style="background:#16a34a; color:white; border:none; padding:6px 12px;">Done</button>
                </div>`;
        }

        const div = document.createElement('div');
        div.className = 'exercise';
        div.innerHTML = `
            <div class="exercise-header">
                <div class="ex-name">${ex.name}</div>
                <button onclick="showFormNotes('${ex.name}')" style="font-size:24px; background:none; border:none; width:40px;">ℹ</button>
            </div>
            ${setsHTML}
        `;
        list.appendChild(div);
    });
}

function adjustValue(exIndex, setNum, type, delta) {
    const id = type === 'weight' ? `w-${exIndex}-${setNum}` : `r-${exIndex}-${setNum}`;
    const el = document.getElementById(id);
    if (!el) return;
    let val = parseInt(el.textContent) || (type === 'weight' ? 135 : 10);
    val = Math.max(type === 'weight' ? 5 : 1, val + delta);
    el.textContent = val;
}

function completeSet(exIndex, setNum) {
    const row = document.getElementById(`set-${exIndex}-${setNum}`);
    if (row) row.style.backgroundColor = '#f0fdf4';
}

function showFormNotes(name) {
    alert(`Form notes for ${name}:\n\n• Full ROM\n• Controlled lowering (2-3 sec)\n• Squeeze at peak\n\nPersonal note space coming soon.`);
}

function finishWorkout() {
    if (confirm("Finish workout and save?")) {
        currentCycleIndex = (currentCycleIndex + 1) % cycle.length;
        alert("✅ Workout saved! Moving to next day.");
        startWorkout();
    }
}

// Start the app
startWorkout();