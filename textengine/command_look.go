package textengine

import (
	"fmt"
	"strings"
)

type lookDescriptor struct {
	entity EntityRef
	look_type string
	look_desc string
}

func (entity EntityRef) getLooks() ([]lookDescriptor, error) {
	var looks []lookDescriptor

	rows, err := entity.game.database.Query("SELECT l.entityid, l.type, l.desc FROM entity_look l WHERE l.entityid IN (SELECT r.recieverid FROM entity_relationship r WHERE r.providerid IN (SELECT r2.providerid FROM entity_relationship r2 WHERE r2.recieverid = ? AND r2.alive = 1 AND r2.relationshipverb = 'contains') AND r.relationshipverb = 'contains' AND r.alive = 1) OR l.entityid IN (SELECT r.providerid FROM entity_relationship r WHERE r.alive = 1 AND r.relationshipverb = 'contains' AND r.recieverid = ?)", entity.id, entity.id)

	if err != nil {
		return looks, err
	}

	defer rows.Close()

	for rows.Next() {
		var look lookDescriptor
		var entity_id string

		if err := rows.Scan(&entity_id, &look.look_type, &look.look_desc); err != nil {
			return looks, err
		}

		look.entity = entity.game.GetEntity(entity_id)

		looks = append(looks, look)
	}

	if err = rows.Err(); err != nil {
		return looks, err
	}

	return looks, nil
}

func CommandLookRegister(game *Game, commands *Commands) {
	command := commands.Register("look", func(client *Client, args CommandInput) {
		looks, err := client.entity.getLooks()
		if err != nil {
			client.SendAll(CommandOutput{}, args, err)
			return
		}

		var looksText []string

		var output = make(CommandOutput)

		for _, look := range looks {
			looksText = append(looksText, fmt.Sprintf("%s", look.look_desc))
			output[look.entity.id] = fmt.Sprintf("%q %q", look.look_type, look.look_desc)
		}

		output["text"] = strings.Join(looksText, "\n")

		client.SendAll(output, args, nil)
	})

	command.Register("look", `^look$`, func(args []string) (CommandInput, error) {
		return CommandInput{}, nil
	})
}
