The chat application in this branch adopts a state-driven architecture, where both the client and server transition through three synchronized stages:
- User Authentication
- Chat Partner Identification
- Chatting 

Each component—client and server—relies on its current state to determine behavior and communication. However, this tight coupling means that any minor disruption in the connection forces users to restart the entire flow, beginning again from the login stage.
Therefore, this architecture was replaced by a stateless one which use server requests to trigger these operations.
