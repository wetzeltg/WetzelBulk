// WetzelBulk - Better Superset Display
const cycle = ['Push', 'Legs', 'Pull', 'Cardio'];
let currentCycleIndex = 0;

const routine = {
    Push: [
        { group: "Incline Chest / Lateral Raise Superset", exercises: [
            { name: "Inc. Chest", fullName: "Incline Chest Press", sets: 4 },
            { name: "Lat. Raise", fullName: "Lateral Raises", sets: 4 }
        ]},
        { group: "Flat Chest / Military Press Superset", exercises: [
            { name: "Flat Chest", fullName: "Flat Chest Press", sets: 3 },
            { name: "Mil. Press", fullName: "Military Press", sets: 3 }
        ]},
        { group: "Tricep Superset", exercises: [
            { name: "Pushdowns", fullName: "Tricep Pushdowns", sets: 3 },
            { name: "Skull", fullName: "Skull Crushers", sets: 3 }
        ]}
    ],
    Legs: [
        { group: "Legs", exercises: [
            { name: "Leg Press", fullName: "Leg Press", sets: 4 },
            { name: "RDL", fullName: "Romanian Deadlifts (DB)", sets: 3 },
            { name: "Calf Raise", fullName: "Calf Raises", sets: 4 }
        ]},
        { group: "Core", exercises: [
            { name: "Crunches + Back Ext", fullName: "Crunches + Back Extensions", sets: 3 }
        ]}
    ],
    // Add Pull later if needed
    Pull: [],
    Cardio: [{ group: "Cardio", exercises: [{ name: "Incline Walk", fullName: "Incline Treadmill Walk", sets: 1 }] }]
};

function startWorkout() {
    const workout = cycle[currentCycleIndex];
    document.getElementById('workout-title').textContent = workout + " Day";
    renderExercises(workout);
}

function renderExercises(workoutType) {
    const list = document.getElementById('exercises-list');
    list.innerHTML = '';

    const groups = routine[workoutType] || [];

    groups.forEach(group => {
        const groupDiv = document.createElement('div');
        groupDiv.className = 'exercise';
        groupDiv.style.marginBottom = '16px';

        let setsHTML = '';

        group.exercises.forEach((ex, exIndex) => {
            for (let s = 1; s <= ex.sets; s++) {
                setsHTML += `
                    <div class="set-row" id="set-${group.exercises.indexOf(ex)}-${s}">
                        <div style="font-weight:500; min-width:80px;">${ex.name}</div>
                        <div style="display:flex; gap:4px; align-items:center;">
                            <button onclick="adjust(this, -5)" style="width:34px;">–</button>
                            <span class="weight-val" style="width:48px;text-align:center;font-family:monospace;">135</span>
                            <button onclick="adjust(this, 5)" style="width:34px;">+</button>
                        </div>
                        <div style="display:flex; gap:4px; align-items:center;">
                            <button onclick="adjustReps(this, -1)" style="width:34px;">–</button>
                            <span class="reps-val" style="width:40px;text-align:center;font-family:monospace;">10</span>
                            <button onclick="adjustReps(this, 1)" style="width:34px;">+</button>
                        </div>
                        <button onclick="completeSet(this)" style="background:#16a34a;color:white;border:none;padding:6px 12px;">Done</button>
                    </div>`;
            }
        });

        groupDiv.innerHTML = `
            <div class="exercise-header">
                <div style="font-weight:600;">${group.group}</div>
                <button class="info-btn" onclick="showFormNotes('${group.exercises[0].fullName}')">ⓘ</button>
            </div>
            ${setsHTML}
        `;
        list.appendChild(groupDiv);
    });
}

// Simple helper functions (will improve later)
function adjust(btn, delta) {
    const valSpan = btn.parentElement.querySelector('.weight-val');
    let val = parseInt(valSpan.textContent);
    val = Math.max(5, val + delta);
    valSpan.textContent = val;
}

function adjustReps(btn, delta) {
    const valSpan = btn.parentElement.querySelector('.reps-val');
    let val = parseInt(valSpan.textContent);
    val = Math.max(1, val + delta);
    valSpan.textContent = val;
}

function completeSet(btn) {
    btn.style.background = '#86efac';
    btn.textContent = '✓';
}

function showFormNotes(name) {
    alert(`Form notes for ${name}`);
}

function finishWorkout() {
    if (confirm("Finish workout?")) {
        currentCycleIndex = (currentCycleIndex + 1) % cycle.length;
        startWorkout();
    }
}

// Start
startWorkout();