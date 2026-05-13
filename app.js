// WetzelBulk
const cycle = ['Push', 'Legs', 'Pull', 'Cardio'];
let currentCycleIndex = 0;

const routine = {
    Push: [{ name: "Incline Chest Press", sets: 4 }, { name: "Lateral Raises", sets: 4 },
           { name: "Flat Chest Press", sets: 3 }, { name: "Military Press", sets: 3 },
           { name: "Tricep Pushdowns", sets: 3 }, { name: "Skull Crushers", sets: 3 }],
    Legs: [{ name: "Leg Press", sets: 4 }, { name: "Romanian Deadlifts (DB)", sets: 3 },
           { name: "Calf Raises", sets: 4 }, { name: "Crunches + Back Extensions", sets: 3 }],
    Pull: [{ name: "Lat Pulldowns (wide)", sets: 4 }, { name: "Face Pulls", sets: 4 },
           { name: "Cable Rows", sets: 3 }, { name: "Dumbbell Shrugs", sets: 3 },
           { name: "Incline DB Curls", sets: 3 }, { name: "Hammer Curls", sets: 3 }],
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

    routine[workoutType].forEach((ex, exIndex) => {
        let setsHTML = '';
        for (let s = 1; s <= ex.sets; s++) {
            setsHTML += `
                <div class="set-row" id="set-${exIndex}-${s}">
                    <div style="font-weight:500;">Set ${s}</div>
                    <div style="text-align:center">
                        <button onclick="adjust(${exIndex},${s},'w',-5)" class="btn-minus">–</button>
                        <span id="w-${exIndex}-${s}" style="display:inline-block;width:50px;font-family:monospace;">135</span>
                        <button onclick="adjust(${exIndex},${s},'w',5)" class="btn-plus">+</button>
                    </div>
                    <div style="text-align:center">
                        <button onclick="adjust(${exIndex},${s},'r',-1)" class="btn-minus">–</button>
                        <span id="r-${exIndex}-${s}" style="display:inline-block;width:40px;font-family:monospace;">10</span>
                        <button onclick="adjust(${exIndex},${s},'r',1)" class="btn-plus">+</button>
                    </div>
                    <button onclick="completeSet(${exIndex},${s})" style="background:#16a34a;color:white;border:none">Done</button>
                </div>`;
        }

        const div = document.createElement('div');
        div.className = 'exercise';
        div.innerHTML = `
            <div class="exercise-header">
                <div class="ex-name">${ex.name}</div>
                <button class="info-btn" onclick="showFormNotes('${ex.name}')">ⓘ</button>
            </div>
            ${setsHTML}
        `;
        list.appendChild(div);
    });
}

function adjust(exIndex, setNum, type, delta) {
    const prefix = type === 'w' ? 'w' : 'r';
    const el = document.getElementById(`${prefix}-${exIndex}-${setNum}`);
    let val = parseInt(el.textContent) || 135;
    val = Math.max(type === 'w' ? 5 : 1, val + delta);
    el.textContent = val;
}

function completeSet(exIndex, setNum) {
    const row = document.getElementById(`set-${exIndex}-${setNum}`);
    if (row) row.style.backgroundColor = '#f0fdf4';
}

function showFormNotes(name) {
    alert(`Form notes for ${name}:\n\n• Full range of motion\n• Slow controlled lowering\n• Strong squeeze at top`);
}

function finishWorkout() {
    if (confirm("Finish workout and go to next day?")) {
        currentCycleIndex = (currentCycleIndex + 1) % cycle.length;
        startWorkout();
    }
}

// Start
startWorkout();