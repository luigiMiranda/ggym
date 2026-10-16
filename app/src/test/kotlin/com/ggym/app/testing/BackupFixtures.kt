package com.ggym.app.testing

/**
 * A backup document exactly as **format 1** wrote it: `schemaVersion` 1, and no `weightKg` key
 * anywhere in the routine tree, because in format 1 a slot had no weight of its own.
 *
 * It is written out by hand on purpose. The point of keeping it is that no current code can
 * generate it any more, and a test that rebuilt the old format with today's writer would prove
 * nothing about the reader.
 */
internal fun version1BackupJson(): String = """
    {
      "schemaVersion": 1,
      "exportedAt": 1760000000000,
      "appVersion": "0.1.0",
      "exercises": [
        {
          "id": 1,
          "name": "Bench Press",
          "currentWorkingWeight": 87.5,
          "defaultRestSeconds": 90
        }
      ],
      "routines": [
        {
          "id": 1,
          "name": "Push Day",
          "createdAt": 1750000000000,
          "days": [
            {
              "id": 1,
              "dayName": "Push A",
              "orderIndex": 0,
              "exercises": [
                {
                  "id": 1,
                  "exerciseId": 1,
                  "targetSets": 4,
                  "targetReps": "8-10",
                  "restSeconds": 120,
                  "orderIndex": 0
                }
              ]
            }
          ]
        }
      ],
      "sessions": [
        {
          "id": 1,
          "dateTimestamp": 1759000000000,
          "routineNameSnapshot": "Push Day",
          "dayNameSnapshot": "Push A",
          "durationSeconds": 3120,
          "exercises": [
            {
              "id": 1,
              "exerciseId": 1,
              "exerciseNameSnapshot": "Bench Press",
              "wasCompleted": true,
              "orderIndex": 0,
              "sets": [
                {
                  "id": 1,
                  "setNumber": 1,
                  "weight": 82.5,
                  "reps": 10,
                  "isCompleted": true
                }
              ]
            }
          ]
        }
      ],
      "bodyWeights": [
        {
          "id": 1,
          "dateTimestamp": 1759000000000,
          "weightKg": 82.4
        }
      ]
    }
""".trimIndent()
