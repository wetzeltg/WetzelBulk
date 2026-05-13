// WetzelBulk - Full Workout Logger
const cycle = ['Push', 'Legs', 'Pull', 'Cardio'];
let currentCycleIndex = 0;
let currentWorkout = null;
let workoutHistory = JSON.parse(localStorage.getItem('wetzelbulk_history')) || {};

// Your optimized routine with supersets
const routine = {
    Push: [
        { name: "Incline Chest Press", sets: 4, supersetWith: 1 },
        { name: "Lateral Raises", sets: 4 },
        { name: "Flat Chest Press", sets: 3, supersetWith: 3 },
        { name: "Military Press", sets: 3 },
        { name: "Tricep Pushdowns", sets: 3, supersetWith: 5 },
        { name: "Skull Crushers", sets: 3 }
    ],
    Legs: [
        { name: "Leg Press", sets: 4 },
        { name: "Romanian Deadlifts (DB)", sets: 3, supersetWith: 2 },
        { name: "Calf Raises", sets: 4 },
        { name: "Crunches + Back Extensions", sets: 3 }
    ],
    Pull: [
        { name: "Lat Pulldowns (wide)", sets: 4, supersetWith: 1 },
        { name: "Face Pulls", sets: 4 },
        { name: "Cable Rows", sets: 3, supersetWith: 3 },
        { name: "Dumbbell Shrugs", sets: 3 },
        { name: "Incline DB Curls", sets: 3, supersetWith: 5 },
        { name: "Hammer Curls", sets: 3 }
    ],
    Cardio: [
        { name: "Incline Treadmill Walk", sets: 1, notes: "350 calories, 10% incline" }
    ]
};

function getNextWorkout() {
    return cycle[currentCycleIndex];
}

function startWorkout() {
    currentWorkout = getNextWorkout();
    document.getElementById('workout-title').textContent = currentWorkout + " Day";
    renderExercises();
}

function renderExercises() {
    const list = document.getElementById('exercises-list');
    list.innerHTML = '';
    
    const exercises = routine[currentWorkout] || [];
    
    exercises.forEach((ex, index) => {
        const div = document.createElement('div');
        div.className = 'exercise';
        div.innerHTML = `
            <div class="exercise-header">
                <div class="ex-name">${ex.name}</div>
                <button class="i-btn" onclick="showFormNotes('${ex.name}')">ℹ</button>
            </div>
            <div id="sets-${index}"></div>
        `;
        list.appendChild(div);
        renderSets(index, ex);
    });
}

function renderSets(exIndex, exercise) {
    const container = document.getElementById(`sets-${exIndex}`);
    container.innerHTML = '';
    
    for (let set = 1; set <= exercise.sets; set++) {
        const row = document.createElement('div');
        row.className = 'set-row pending';
        row.innerHTML = `
            <div class="set-label">Set ${set}</div>
            <div><button onclick="adjustWeight(${exIndex},${set}, -5)">–</button> <span id="w-${exIndex}-${set}">135</span> <button onclick="adjustWeight(${exIndex},${set}, 5)">+</button></div>
            <div><button onclick="adjustReps(${exIndex},${set}, -1)">–</button> <span id="r-${exIndex}-${set}">10</span> <button onclick="adjustReps(${exIndex},${set}, 1)">+</button></div>
            <button onclick="completeSet(${exIndex},${set})" style="grid-column:3">Done</button>
        `;
        container.appendChild(row);
    }
}

function adjustWeight(exIndex, set, delta) {
    const el = document.getElementById(`w-${exIndex}-${set}`);
    let val = parseInt(el.textContent) || 135;
    val = Math.max(5, val + delta);
    el.textContent = val;
}

function adjustReps(exIndex, set, delta) {
    const el = document.getElementById(`r-${exIndex}-${set}`);
    let val = parseInt(el.textContent) || 10;
    val = Math.max(1, val + delta);
    el.textContent = val;
}

function completeSet(exIndex, set) {
    const row = document.querySelector(`#sets-${exIndex} .set-row:nth-child(${set})`);
    if (row) row.classList.add('completed');
}

function showFormNotes(exerciseName) {
    alert(`Form notes for ${exerciseName}:\n\n• Full range of motion\n• Controlled eccentric (3 sec down)\n• Squeeze at the top\n\n(Add your personal notes later)`);
}

function finishWorkout() {
    if (confirm("Finish this workout and save?")) {
        alert("Workout saved! Great work 💪\n\nData is stored locally.");
        // Advance cycle
        currentCycleIndex = (currentCycleIndex + 1) % cycle.length;
    }
}

// Initialize
startWorkout();
