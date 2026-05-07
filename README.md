[![Coverage Status](https://coveralls.io/repos/github/imgiahuy/me_chess/badge.svg?branch=main&dummy=1)](https://coveralls.io/github/imgiahuy/me_chess?branch=main)

This project is an attempt to create a game chess with a strong software structure. The code will be mostly handle by AI and the structure will be considered and changed after time to time to serve a better purpose. 

Beside using AI-Support, it is important to learn faster and not jump to the solution.

For now, the project is in a monolith structure with all code stay in one source, for better control and update. But eventually that will be changed later. The structure used in the project is a layer structure with 4 different layer, which serve a different purpose : 

- domain : all the models
- application : pure function and logic
- presentation : pure UX/UI
- infrastructure : for later persistent and multiplayer or more

With this structure it is easy to manage (update, patch, bugs, ...) the software, and to extend the software.

This is an ongoing project and more function and meanings will be added later as the course go.

Roadmap:

- Improve and expand Parser
- Imporve web ui and add docker-compose
- Persistent
- Performance
- Bot AI Deployment
